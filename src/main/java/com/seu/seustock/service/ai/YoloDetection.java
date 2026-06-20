package com.seu.seustock.service.ai;

public record YoloDetection(
    String label, Double confidence, Double x1, Double y1, Double x2, Double y2) {}
