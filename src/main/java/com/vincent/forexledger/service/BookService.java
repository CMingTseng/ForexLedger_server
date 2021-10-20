package com.vincent.forexledger.service;

import com.vincent.forexledger.exception.NotFoundException;
import com.vincent.forexledger.model.book.BookDetailResponse;
import com.vincent.forexledger.model.book.BookListResponse;
import com.vincent.forexledger.model.book.CreateBookRequest;
import com.vincent.forexledger.repository.BookRepository;
import com.vincent.forexledger.security.UserIdentity;
import com.vincent.forexledger.util.converter.BookConverter;
import org.springframework.data.util.Pair;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public class BookService {
    private final UserIdentity userIdentity;
    private final BookRepository repository;
    private final ExchangeRateTable exchangeRateTable;

    public BookService(UserIdentity userIdentity, BookRepository repository,
                       ExchangeRateTable exchangeRateTable) {
        this.userIdentity = userIdentity;
        this.repository = repository;
        this.exchangeRateTable = exchangeRateTable;
    }

    public String createBook(CreateBookRequest request) {
        var book = BookConverter.toBook(request);
        book.setCreator(userIdentity.getId());
        book.setCreatedTime(new Date());

        repository.insert(book);

        return book.getId();
    }

    public List<BookListResponse> loadMyBooks() {
        var books = repository.findByCreator(userIdentity.getId());
        var bankCurrencyTypePairs = books.stream()
                .map(book -> Pair.of(book.getBank(), book.getCurrencyType()))
                .collect(Collectors.toSet());

        return BookConverter.toBookListResponses(books, exchangeRateTable.getBuyingRates(bankCurrencyTypePairs));
    }

    public BookDetailResponse loadBookDetail(String id) {
        var book = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Can't find book."));
        var exchangeRate = exchangeRateTable.get(book.getBank(), book.getCurrencyType());

        return BookConverter.toBookDetail(book, exchangeRate.getBuyingRate());
    }
}
