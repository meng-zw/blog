package com.blog.taxonomy;

import com.blog.shared.persistence.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity(name = "TaxonomyCategory")
@Table(name = "category")
public class Category extends AuditedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "normalized_name", nullable = false, length = 120, unique = true)
    private String normalizedName;

    @Column(nullable = false, unique = true, length = 160)
    private String slug;

    @Column
    private String description;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CategoryScope scope;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getNormalizedName() { return normalizedName; }
    public void setNormalizedName(String normalizedName) { this.normalizedName = normalizedName; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public CategoryScope getScope() { return scope; }
    public void setScope(CategoryScope scope) { this.scope = scope; }
}
