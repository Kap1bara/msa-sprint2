package com.hotelio.history.http;

import com.hotelio.history.db.BookingHistoryEntity;
import com.hotelio.history.db.BookingHistoryRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class HistoryController {

    private final BookingHistoryRepository repo;

    public HistoryController(BookingHistoryRepository repo) {
        this.repo = repo;
    }

    @GetMapping("/history")
    public List<BookingHistoryEntity> history(@RequestParam(defaultValue = "50") int limit) {
        int safe = Math.max(1, Math.min(limit, 500));
        return repo.findAll(PageRequest.of(0, safe)).getContent();
    }
}
