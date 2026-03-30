package com.maple.service.sftp;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import com.maple.config.MapleSftpProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Vector;

/**
 * SFTP upload/download using JSch (mwiede fork). Used for outbound batch files and inbound receipts.
 */
@Component
public class SftpFileTransferClient {

    private static final Logger logger = LoggerFactory.getLogger(SftpFileTransferClient.class);

    private final MapleSftpProperties properties;

    public SftpFileTransferClient(MapleSftpProperties properties) {
        this.properties = properties;
    }

    /**
     * Uploads data to {@code maple.sftp.remote-dir} + "/" + relativePath (relativePath must not start with /).
     */
    public void uploadUnderRemoteDir(String relativePath, byte[] data) throws Exception {
        String full = fullRemotePath(relativePath);
        executeWithSession(
                channelSftp -> {
                    mkdirParents(channelSftp, full);
                    try (ByteArrayInputStream in = new ByteArrayInputStream(data)) {
                        channelSftp.put(in, full);
                    }
                    return null;
                });
        logger.debug("SFTP uploaded {} bytes to {}", data.length, full);
    }

    public boolean fileExists(String relativePath) throws Exception {
        String full = fullRemotePath(relativePath);
        boolean[] found = {false};
        executeWithSession(
                channelSftp -> {
                    try {
                        channelSftp.stat(full);
                        found[0] = true;
                    } catch (SftpException e) {
                        if (e.id != ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                            throw e;
                        }
                    }
                    return null;
                });
        return found[0];
    }

    public byte[] download(String relativePath) throws Exception {
        String full = fullRemotePath(relativePath);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        executeWithSession(
                channelSftp -> {
                    channelSftp.get(full, out);
                    return null;
                });
        return out.toByteArray();
    }

    public List<String> listNamesInSubdir(String relativeDir) throws Exception {
        String fullDir = fullRemotePath(relativeDir);
        List<String> names = new ArrayList<>();
        executeWithSession(
                channelSftp -> {
                    @SuppressWarnings("unchecked")
                    Vector<ChannelSftp.LsEntry> entries = channelSftp.ls(fullDir);
                    for (ChannelSftp.LsEntry e : entries) {
                        if (!".".equals(e.getFilename()) && !"..".equals(e.getFilename())) {
                            names.add(e.getFilename());
                        }
                    }
                    return null;
                });
        return names;
    }

    private String fullRemotePath(String relativePath) {
        String rel = relativePath == null ? "" : relativePath.replaceFirst("^/+", "");
        String base = properties.getRemoteDir().replaceAll("/+$", "");
        return base + "/" + rel;
    }

    private void mkdirParents(ChannelSftp channel, String fullFilePath) throws SftpException {
        int slash = fullFilePath.lastIndexOf('/');
        if (slash <= 0) {
            return;
        }
        String dir = fullFilePath.substring(0, slash);
        String[] parts = dir.split("/");
        StringBuilder path = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) {
                continue;
            }
            path.append("/").append(p);
            try {
                channel.mkdir(path.toString());
            } catch (SftpException e) {
                // already exists
            }
        }
    }

    private <T> T executeWithSession(SftpCallback<T> callback) throws Exception {
        JSch jsch = new JSch();
        MapleSftpProperties.BatchDelivery b = properties.getBatch();
        if (b.isStrictHostKeyChecking() && b.getKnownHostsPath() != null && !b.getKnownHostsPath().isBlank()) {
            jsch.setKnownHosts(b.getKnownHostsPath());
        }
        Session session =
                jsch.getSession(properties.getUsername(), properties.getHost(), properties.getPort());
        session.setPassword(properties.getPassword());
        Properties config = new Properties();
        config.put("StrictHostKeyChecking", b.isStrictHostKeyChecking() ? "yes" : "no");
        session.setConfig(config);
        session.setTimeout(properties.getSessionTimeout());
        session.connect(properties.getConnectTimeout());
        ChannelSftp channel = null;
        try {
            channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect(properties.getConnectTimeout());
            return callback.apply(channel);
        } finally {
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
            if (session.isConnected()) {
                session.disconnect();
            }
        }
    }

    @FunctionalInterface
    private interface SftpCallback<T> {
        T apply(ChannelSftp channelSftp) throws Exception;
    }
}
