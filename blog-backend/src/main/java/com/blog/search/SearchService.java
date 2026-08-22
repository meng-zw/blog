package com.blog.search;

import com.blog.search.dto.SearchResultResponse;
import com.blog.shared.web.PageResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Clock;

@Service
@Transactional(readOnly = true)
public class SearchService {
    private final SearchRepository searchRepository;
    private final Clock clock;

    @Autowired
    public SearchService(SearchRepository searchRepository) {
        this(searchRepository, Clock.systemUTC());
    }

    SearchService(SearchRepository searchRepository, Clock clock) {
        this.searchRepository = searchRepository;
        this.clock = clock;
    }

    public PageResponse<SearchResultResponse> search(String query, int page, int requestedSize) {
        if (page < 0) {
            throw new IllegalArgumentException("Search page must be zero or greater");
        }
        String normalized = query == null ? "" : Normalizer.normalize(query, Normalizer.Form.NFKC).trim();
        if (normalized.isEmpty() || normalized.length() > 100) {
            throw new IllegalArgumentException("Search query length after normalization must be between 1 and 100");
        }
        int size = Math.clamp(requestedSize, 1, 50);
        SearchRepository.SearchPage result = searchRepository.search(normalized, page, size, clock.instant());
        long pages = result.total() == 0 ? 0 : ((result.total() - 1) / size) + 1;
        return new PageResponse<>(result.items(), page, size, result.total(),
                (int) Math.min(Integer.MAX_VALUE, pages));
    }
}
