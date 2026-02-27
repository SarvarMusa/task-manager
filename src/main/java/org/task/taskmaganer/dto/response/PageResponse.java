package org.task.taskmaganer.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;

@Schema(description = "Sayfalı liste yanıt modeli")
public class PageResponse<T> {

    @Schema(description = "Sayfadaki içerik listesi")
    private List<T> content;

    @Schema(description = "Mevcut sayfa numarası (0'dan başlar)", example = "0")
    private int pageNumber;

    @Schema(description = "Sayfa başına öğe sayısı", example = "10")
    private int pageSize;

    @Schema(description = "Toplam öğe sayısı", example = "100")
    private long totalElements;

    @Schema(description = "Toplam sayfa sayısı", example = "10")
    private int totalPages;

    @Schema(description = "Son sayfa mı?", example = "false")
    private boolean last;

    @Schema(description = "İlk sayfa mı?", example = "true")
    private boolean first;

    @Schema(description = "Boş mu?", example = "false")
    private boolean empty;

    public PageResponse() {}

    public PageResponse(Page<T> page) {
        this.content = page.getContent();
        this.pageNumber = page.getNumber();
        this.pageSize = page.getSize();
        this.totalElements = page.getTotalElements();
        this.totalPages = page.getTotalPages();
        this.last = page.isLast();
        this.first = page.isFirst();
        this.empty = page.isEmpty();
    }

    public PageResponse(List<T> content, int pageNumber, int pageSize, long totalElements, int totalPages, boolean last, boolean first, boolean empty) {
        this.content = content;
        this.pageNumber = pageNumber;
        this.pageSize = pageSize;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
        this.last = last;
        this.first = first;
        this.empty = empty;
    }

    public List<T> getContent() {
        return content;
    }

    public void setContent(List<T> content) {
        this.content = content;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(int pageNumber) {
        this.pageNumber = pageNumber;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public void setTotalElements(long totalElements) {
        this.totalElements = totalElements;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }

    public boolean isLast() {
        return last;
    }

    public void setLast(boolean last) {
        this.last = last;
    }

    public boolean isFirst() {
        return first;
    }

    public void setFirst(boolean first) {
        this.first = first;
    }

    public boolean isEmpty() {
        return empty;
    }

    public void setEmpty(boolean empty) {
        this.empty = empty;
    }
}
