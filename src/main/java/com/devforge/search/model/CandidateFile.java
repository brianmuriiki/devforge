package com.devforge.search.model;

/** A file that shares its size with at least one other file (a possible duplicate). */
public record CandidateFile(long id, long projectId, String path, long size, String hash) {}
