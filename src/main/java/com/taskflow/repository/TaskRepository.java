package com.taskflow.repository;

import com.taskflow.entity.Priority;
import com.taskflow.entity.Task;
import com.taskflow.entity.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {

    @Query("select t from Task t join fetch t.project p left join fetch t.assignee " +
           "where p.id in :projectIds " +
           "and (:projectId is null or p.id = :projectId) " +
           "and (:status is null or t.status = :status) " +
           "and (:priority is null or t.priority = :priority) " +
           "and (:assigneeId is null or t.assignee.id = :assigneeId) " +
           "and (lower(t.title) like :q escape '\\' or lower(coalesce(t.description, '')) like :q escape '\\') " +
           "order by t.createdAt desc")
    List<Task> search(@Param("projectIds") Collection<Long> projectIds,
                      @Param("projectId") Long projectId,
                      @Param("status") TaskStatus status,
                      @Param("priority") Priority priority,
                      @Param("assigneeId") Long assigneeId,
                      @Param("q") String q);

    @Query("select t from Task t join fetch t.project left join fetch t.assignee where t.project.id in :projectIds")
    List<Task> findByProjectIds(@Param("projectIds") Collection<Long> projectIds);

    @Query("select t from Task t join fetch t.project left join fetch t.assignee where t.id in :ids")
    List<Task> findAllWithRelationsByIdIn(@Param("ids") Collection<Long> ids);

    List<Task> findByProjectId(Long projectId);

    long countByProjectId(Long projectId);

    long countByProjectIdAndStatus(Long projectId, TaskStatus status);

    @Query("select t from Task t join fetch t.project left join fetch t.assignee " +
           "where t.dueDate = :date and t.status <> com.taskflow.entity.TaskStatus.COMPLETADA")
    List<Task> findOpenDueOn(@Param("date") LocalDate date);

    @Query("select t from Task t join fetch t.project left join fetch t.assignee " +
           "where t.dueDate < :date and t.status <> com.taskflow.entity.TaskStatus.COMPLETADA")
    List<Task> findOpenOverdue(@Param("date") LocalDate date);

    @Query("select t from Task t join fetch t.project where t.assignee.id = :userId " +
           "and t.status <> com.taskflow.entity.TaskStatus.COMPLETADA order by t.dueDate asc nulls last")
    List<Task> findOpenByAssignee(@Param("userId") Long userId);

    @Query("select t.id from Task t where t.indexed = false")
    List<Long> findUnindexedIds();

    @Query("select t.id from Task t")
    List<Long> findAllIds();

    long countByIndexedTrue();

    @Modifying
    @Transactional
    @Query("update Task t set t.indexed = :indexed where t.id = :id")
    int updateIndexed(@Param("id") Long id, @Param("indexed") boolean indexed);

    @Modifying
    @Transactional
    @Query("update Task t set t.indexed = false")
    int resetIndexed();

    @Modifying
    @Query("update Task t set t.assignee = null where t.assignee.id = :userId and t.project.id = :projectId")
    int unassignUserFromProject(@Param("userId") Long userId, @Param("projectId") Long projectId);
}
