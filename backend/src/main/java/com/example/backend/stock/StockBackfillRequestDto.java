package com.example.backend.stock;

import java.util.List;

public class StockBackfillRequestDto {
    private List<String> codes;
    private String startDate;
    private String endDate;
    private Integer chunkMonths;

    public List<String> getCodes() {
        return codes;
    }

    public void setCodes(List<String> codes) {
        this.codes = codes;
    }

    public String getStartDate() {
        return startDate;
    }

    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    public String getEndDate() {
        return endDate;
    }

    public void setEndDate(String endDate) {
        this.endDate = endDate;
    }

    public Integer getChunkMonths() {
        return chunkMonths;
    }

    public void setChunkMonths(Integer chunkMonths) {
        this.chunkMonths = chunkMonths;
    }
}
