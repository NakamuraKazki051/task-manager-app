package com.taskmanager.api.board;

import com.taskmanager.api.auth.CurrentUser;
import com.taskmanager.api.board.BoardDtos.BoardRequest;
import com.taskmanager.api.board.BoardDtos.BoardResponse;
import com.taskmanager.api.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/boards")
public class BoardController {

    private final BoardRepository boardRepository;
    private final CurrentUser currentUser;

    public BoardController(BoardRepository boardRepository, CurrentUser currentUser) {
        this.boardRepository = boardRepository;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<BoardResponse> list(HttpServletRequest httpRequest) {
        String userId = currentUser.require(httpRequest);
        return boardRepository.findByUserId(userId).stream().map(BoardResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BoardResponse create(@Valid @RequestBody BoardRequest request, HttpServletRequest httpRequest) {
        String userId = currentUser.require(httpRequest);
        Board board = boardRepository.save(new Board(userId, request.name().trim()));
        return BoardResponse.from(board);
    }

    @PutMapping("/{id}")
    public BoardResponse rename(@PathVariable String id, @Valid @RequestBody BoardRequest request, HttpServletRequest httpRequest) {
        String userId = currentUser.require(httpRequest);
        Board board = boardRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("ボードが見つかりません: " + id));
        board.setName(request.name().trim());
        return BoardResponse.from(boardRepository.save(board));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id, HttpServletRequest httpRequest) {
        String userId = currentUser.require(httpRequest);
        if (!boardRepository.existsByIdAndUserId(id, userId)) {
            throw ApiException.notFound("ボードが見つかりません: " + id);
        }
        if (boardRepository.countByUserId(userId) <= 1) {
            throw ApiException.badRequest("最後の1ボードは削除できません");
        }
        boardRepository.deleteById(id);
    }
}
