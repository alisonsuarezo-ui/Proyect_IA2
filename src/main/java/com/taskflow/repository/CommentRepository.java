package com.taskflow.repository;

import com.taskflow.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    @Query("select c from Comment c join fetch c.author where c.task.id = :taskId order by c.createdAt asc")
    List<Comment> findByTaskId(@Param("taskId") Long taskId);

    long countByTaskId(Long taskId);
}
