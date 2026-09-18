package com.taskmanager.api.board;

import com.taskmanager.api.board.BoardDtos.BoardRequest;
import com.taskmanager.api.board.BoardDtos.BoardResponse;
import com.taskmanager.api.common.ApiException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/boards")
public class BoardController {

    private final BoardRepository boardRepository;

    public BoardController(BoardRepository boardRepository) {
        this.boardRepository = boardRepository;
    }

    @GetMapping
    public List<BoardResponse> list() {
        return boardRepository.findAll().stream().map(BoardResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BoardResponse create(@Valid @RequestBody BoardRequest request) {
        Board board = boardRepository.save(new Board(request.name().trim()));
        return BoardResponse.from(board);
    }

    @PutMapping("/{id}")
    public BoardResponse rename(@PathVariable String id, @Valid @RequestBody BoardRequest request) {
        Board board = boardRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("ボードが見つかりません: " + id));
        board.setName(request.name().trim());
        return BoardResponse.from(boardRepository.save(board));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        if (!boardRepository.existsById(id)) {
            throw ApiException.notFound("ボードが見つかりません: " + id);
        }
        if (boardRepository.count() <= 1) {
            throw ApiException.badRequest("最後の1ボードは削除できません");
        }
        boardRepository.deleteById(id);
    }
}
