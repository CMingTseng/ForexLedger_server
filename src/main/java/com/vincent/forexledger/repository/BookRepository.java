package com.vincent.forexledger.repository;

import com.vincent.forexledger.model.book.Book;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookRepository extends MongoRepository<Book, String> {
    List<Book> findByCreator(String creator);
}
