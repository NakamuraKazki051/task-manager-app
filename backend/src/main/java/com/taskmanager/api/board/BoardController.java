package com.taskmanager.api.board;

import com.taskmanager.api.auth.CurrentUser;
import com.taskmanager.api.board.BoardDtos.BoardRequest;
import com.taskmanager.api.board.BoardDtos.BoardResponse;
import com.taskmanager.api.column.BoardColumn;
import com.taskmanager.api.column.BoardColumnRepository;
import com.taskmanager.api.common.ApiException;
import com.taskmanager.api.task.TaskRepository;
import com.taskmanager.api.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/boards")
public class BoardController {

    private final BoardRepository boardRepository;
    private final BoardColumnRepository columnRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final CurrentUser currentUser;

    public BoardController(BoardRepository boardRepository, BoardColumnRepository columnRepository,
                           TaskRepository taskRepository, UserRepository userRepository, CurrentUser currentUser) {
        this.boardRepository = boardRepository;
        this.columnRepository = columnRepository;
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
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

    /**
     * ボードが1つも無いユーザーに、初期ボード「マイボード」と既定の列を作る。既にボードがあれば何もせず一覧を返す。
     * 初回ログイン時に複数タブや二重送信で同時に呼ばれても、ユーザー行のロックで直列化されるので1つしか作られない。
     */
    @PostMapping("/ensure-default")
    @Transactional
    public List<BoardResponse> ensureDefault(HttpServletRequest httpRequest) {
        String userId = currentUser.require(httpRequest);
        userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> ApiException.unauthorized("ログインしていません"));
        List<Board> boards = boardRepository.findByUserId(userId);
        if (boards.isEmpty()) {
            Board board = boardRepository.save(new Board(userId, "マイボード"));
            columnRepository.save(new BoardColumn(board.getId(), "未着手", false, 0));
            columnRepository.save(new BoardColumn(board.getId(), "進行中", false, 1));
            columnRepository.save(new BoardColumn(board.getId(), "完了", true, 2));
            boards = List.of(board);
        }
        return boards.stream().map(BoardResponse::from).toList();
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
    @Transactional
    public void delete(@PathVariable String id, HttpServletRequest httpRequest) {
        String userId = currentUser.require(httpRequest);
        if (!boardRepository.existsByIdAndUserId(id, userId)) {
            throw ApiException.notFound("ボードが見つかりません: " + id);
        }
        if (boardRepository.countByUserId(userId) <= 1) {
            throw ApiException.badRequest("最後の1ボードは削除できません");
        }
        // 列・タスクはボードへの外部キーを持たないため、明示的に消さないとDBに残り続ける
        taskRepository.deleteByBoardId(id);
        columnRepository.deleteByBoardId(id);
        boardRepository.deleteById(id);
    }
}
