package com.najacks.backend.domain.admin.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "static_page_history",
        uniqueConstraints = @UniqueConstraint(name = "uk_static_page_history_slug_version", columnNames = {"slug", "version"}),
        indexes = @Index(name = "idx_static_page_history_slug", columnList = "slug")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class StaticPageHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String slug;

    @Column(nullable = false)
    private Integer version;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column
    private Long editedById;

    @Column(length = 100)
    private String editedByEmail;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime editedAt;
}
