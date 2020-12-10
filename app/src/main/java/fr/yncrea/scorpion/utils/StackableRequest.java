package fr.yncrea.scorpion.utils;

public class StackableRequest {
    private Integer week = 0;
    private boolean forceRequest = false;
    private boolean mustDraw = true;

    public StackableRequest(Integer week, boolean forceRequest, boolean mustDraw) {
        this.week = week;
        this.forceRequest = forceRequest;
        this.mustDraw = mustDraw;
    }

    public Integer getWeek() {
        return week;
    }

    public boolean isForceRequest() {
        return forceRequest;
    }

    public boolean isMustDraw() {
        return mustDraw;
    }
}
