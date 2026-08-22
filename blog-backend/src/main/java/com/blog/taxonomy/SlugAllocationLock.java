package com.blog.taxonomy;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "taxonomy_slug_lock")
public class SlugAllocationLock {
    @Id
    private Long id;
    public Long getId() { return id; }
}
