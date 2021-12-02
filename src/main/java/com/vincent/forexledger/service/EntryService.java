package com.vincent.forexledger.service;

import com.vincent.forexledger.exception.BadRequestException;
import com.vincent.forexledger.exception.InsufficientBalanceException;
import com.vincent.forexledger.model.book.Book;
import com.vincent.forexledger.model.entry.CreateEntryRequest;
import com.vincent.forexledger.model.entry.Entry;
import com.vincent.forexledger.repository.EntryRepository;
import com.vincent.forexledger.security.UserIdentity;
import com.vincent.forexledger.util.converter.EntryConverter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class EntryService {
    private UserIdentity userIdentity;
    private EntryRepository repository;
    private BookService bookService;

    public EntryService(UserIdentity userIdentity, EntryRepository repository, BookService bookService) {
        this.userIdentity = userIdentity;
        this.repository = repository;
        this.bookService = bookService;
    }

    @Transactional
    public String createEntry(CreateEntryRequest request) {
        validate(request);

        var involvedBookIds = Stream.of(request.getBookId(), request.getRelatedBookId())
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        var entries = new ArrayList<Entry>(involvedBookIds.size());
        var books = bookService.loadBooksByIds(involvedBookIds);
        var bookMap = books.stream()
                .collect(Collectors.toMap(Book::getId, Function.identity()));

        var primaryEntry = EntryConverter.toEntry(request);
        primaryEntry.setCreator(userIdentity.getId());
        primaryEntry.setCreatedTime(new Date());
        entries.add(primaryEntry);

        if (involvedBookIds.size() > 1) {
            var transferOutBook = primaryEntry.getTransactionType().isTransferIn()
                    ? bookMap.get(request.getRelatedBookId())
                    : bookMap.get(request.getBookId());
            var relatedEntry = toRelatedBookEntry(transferOutBook, primaryEntry);
            primaryEntry.setTwdAmount(relatedEntry.getTwdAmount());
            entries.add(relatedEntry);
            repository.insert(entries);
        } else {
            repository.insert(primaryEntry);
        }

        var bookToEntryMap = entries.stream()
                .collect(Collectors.toMap(entry -> bookMap.get(entry.getBookId()), Function.identity()));
        bookService.updateMetaData(bookToEntryMap);

        return primaryEntry.getId();
    }

    private Entry toRelatedBookEntry(Book transferOutBook, Entry primaryBookEntry) {
        if (primaryBookEntry.getTransactionType().isTransferIn()
                && transferOutBook.getBalance() < primaryBookEntry.getRelatedBookForeignAmount()) {
            throw new InsufficientBalanceException(transferOutBook.getBalance(), primaryBookEntry.getRelatedBookForeignAmount());
        }

        return EntryConverter
                .toRelatedBookEntry(transferOutBook, primaryBookEntry);
    }

    // TODO: refactor
    private void validate(CreateEntryRequest request) {
        var isNotValid = false;
        var transactionType = request.getTransactionType();

        switch (transactionType) {
            case TRANSFER_IN_FROM_TWD:
            case TRANSFER_OUT_TO_TWD:
                var twdAmount = request.getTwdAmount();
                isNotValid = twdAmount == null ||
                        twdAmount <= 0 ||
                        StringUtils.isNotBlank(request.getRelatedBookId()) ||
                        request.getRelatedBookForeignAmount() != null;
                break;
            case TRANSFER_IN_FROM_FOREIGN:
            case TRANSFER_OUT_TO_FOREIGN:
                var relatedBookForeignAmount =
                        Optional.ofNullable(request.getRelatedBookForeignAmount())
                                .orElse(0.0);
                twdAmount = Optional.ofNullable(request.getTwdAmount()).orElse(0);
                var isNotRelatingBook = StringUtils.isBlank(request.getRelatedBookId()) ||
                        request.getRelatedBookForeignAmount() == null;
                isNotValid = (StringUtils.isBlank(request.getRelatedBookId()) ^
                        relatedBookForeignAmount <= 0) ||
                        (isNotRelatingBook && twdAmount <= 0);
                break;
            case TRANSFER_IN_FROM_INTEREST:
                isNotValid = request.getTwdAmount() != null ||
                        StringUtils.isNotBlank(request.getRelatedBookId()) ||
                        request.getRelatedBookForeignAmount() != null;
                break;
            case TRANSFER_IN_FROM_OTHER:
            case TRANSFER_OUT_TO_OTHER:
                isNotValid = request.getTwdAmount() != null ||
                        StringUtils.isNotBlank(request.getRelatedBookId()) ||
                        request.getRelatedBookForeignAmount() != null;
                break;
        }

        if (isNotValid) {
            var msg = String.format("Incorrect data for entry of %s type.", transactionType.name());
            throw new BadRequestException(msg);
        }
    }
}