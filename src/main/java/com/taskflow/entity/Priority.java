package com.taskflow.entity;

public enum Priority {
    BAJA(1), MEDIA(2), ALTA(3), URGENTE(4);

    private final int weight;
    Priority(int weight) { this.weight = weight; }
    public int getWeight() { return weight; }
}
