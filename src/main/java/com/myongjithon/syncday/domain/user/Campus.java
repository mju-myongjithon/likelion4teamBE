package com.myongjithon.syncday.domain.user;

public enum Campus {
    HUMANITIES("인문캠"),
    NATURAL("자연캠");

    private final String label;
    Campus(String label) { this.label = label; }
    public String getLabel() { return label; }
}