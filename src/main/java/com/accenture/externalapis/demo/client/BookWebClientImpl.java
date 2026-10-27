package com.accenture.externalapis.demo.client;

import com.accenture.externalapis.demo.config.ExternalServiceProperties;
import com.accenture.externalapis.demo.dto.BookApiResponse;
import com.accenture.externalapis.demo.dto.BookDto;
import io.netty.channel.ChannelOption;
import java.time.Duration;
import java.util.List;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.util.function.Tuple2;

@Component
public class BookWebClientImpl implements BookWebClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    private final WebClient webClient;

    public BookWebClientImpl(WebClient.Builder builder, ExternalServiceProperties properties) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3_000)
                .responseTimeout(REQUEST_TIMEOUT);
        this.webClient = builder
                .baseUrl(properties.baseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    @Override
    public Mono<BookDto> getBookAsync(Long id) {
        Mono<BookDto> request = webClient.get()
                .uri("/books/{id}", id)
                .retrieve()
                .bodyToMono(BookApiResponse.class)
                .switchIfEmpty(Mono.error(new ClientException(
                        "External book service returned an empty response for book " + id)))
                .map(BookWebClientImpl::toDto);
        return mapErrors(request, "fetch book " + id);
    }

    @Override
    public Flux<BookDto> getAllBooksAsync() {
        Mono<BookApiResponse[]> response = webClient.get()
                .uri("/books")
                .retrieve()
                .bodyToMono(BookApiResponse[].class)
                .switchIfEmpty(Mono.error(new ClientException(
                        "External book service returned an empty book list response")));
        Flux<BookDto> request = response.flatMapMany(Flux::fromArray)
                .map(BookWebClientImpl::toDto);
        return mapErrors(request, "fetch books");
    }

    @Override
    public Mono<List<BookDto>> getBooksInParallel(Long id1, Long id2) {
        Mono<List<BookDto>> request = Mono.zip(getBookAsync(id1), getBookAsync(id2))
                .map(BookWebClientImpl::toList);
        return mapErrors(request, "fetch books " + id1 + " and " + id2);
    }

    private static BookDto toDto(BookApiResponse book) {
        return new BookDto(book.title(), book.author(), book.genre(), book.price());
    }

    private static List<BookDto> toList(Tuple2<BookDto, BookDto> books) {
        return List.of(books.getT1(), books.getT2());
    }

    private static <T> Mono<T> mapErrors(Mono<T> request, String operation) {
        return request
                .timeout(REQUEST_TIMEOUT)
                .onErrorMap(ex -> ex instanceof ClientException
                        ? ex
                        : new ClientException("Failed to " + operation
                                + " from external book service", ex));
    }

    private static <T> Flux<T> mapErrors(Flux<T> request, String operation) {
        return request
                .timeout(REQUEST_TIMEOUT)
                .onErrorMap(ex -> ex instanceof ClientException
                        ? ex
                        : new ClientException("Failed to " + operation
                                + " from external book service", ex));
    }
}
