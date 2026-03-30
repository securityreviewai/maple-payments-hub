package com.maple.repository;

import com.maple.model.RoleDefaultWorkspaceView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RoleDefaultWorkspaceViewRepository extends JpaRepository<RoleDefaultWorkspaceView, String> {}
