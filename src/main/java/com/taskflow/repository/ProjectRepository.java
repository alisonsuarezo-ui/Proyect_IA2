package com.taskflow.repository;

import com.taskflow.entity.Project;
import com.taskflow.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    @Query("select distinct p from Project p left join p.members m " +
           "where p.owner = :user or m = :user order by p.createdAt desc")
    List<Project> findAccessible(@Param("user") User user);

    List<Project> findAllByOrderByCreatedAtDesc();

    @Query("select distinct p.id from Project p left join p.members m where p.owner = :user or m = :user")
    List<Long> findAccessibleIds(@Param("user") User user);

    @Query("select p.id from Project p")
    List<Long> findAllIds();
}
