package com.taskflow.service;

import com.taskflow.dto.TaskDtos.CommentDto;
import com.taskflow.dto.TaskDtos.CommentRequest;
import com.taskflow.dto.UserDtos.UserSummary;
import com.taskflow.entity.Comment;
import com.taskflow.entity.Task;
import com.taskflow.entity.User;
import com.taskflow.exception.ApiException;
import com.taskflow.repository.CommentRepository;
import com.taskflow.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CommentService {

    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final AccessService accessService;

    public CommentService(CommentRepository commentRepository, UserRepository userRepository, AccessService accessService) {
        this.commentRepository = commentRepository;
        this.userRepository = userRepository;
        this.accessService = accessService;
    }

    @Transactional(readOnly = true)
    public List<CommentDto> list(Long taskId, User user) {
        accessService.requireTaskAccess(taskId, user);
        return commentRepository.findByTaskId(taskId).stream().map(c -> toDto(c, user)).toList();
    }

    @Transactional
    public CommentDto add(Long taskId, CommentRequest req, User user) {
        Task task = accessService.requireTaskAccess(taskId, user);
        Comment comment = new Comment();
        comment.setTask(task);
        comment.setAuthor(userRepository.getReferenceById(user.getId()));
        comment.setContent(req.content().trim());
        commentRepository.save(comment);
        return toDto(comment, user);
    }

    @Transactional
    public void delete(Long commentId, User user) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> ApiException.notFound("Comentario no encontrado"));
        accessService.requireTaskAccess(comment.getTask().getId(), user);
        if (!canDelete(comment, user)) {
            throw ApiException.forbidden("Solo el autor o un administrador puede eliminar el comentario");
        }
        commentRepository.delete(comment);
    }

    private boolean canDelete(Comment c, User user) {
        return user.isAdmin() || c.getAuthor().getId().equals(user.getId());
    }

    private CommentDto toDto(Comment c, User viewer) {
        return new CommentDto(c.getId(), c.getContent(), UserSummary.from(c.getAuthor()), canDelete(c, viewer), c.getCreatedAt());
    }
}
