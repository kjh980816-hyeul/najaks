package com.najacks.backend.domain.content.entity;

import com.najacks.backend.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "contents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Content {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "streamer_id", nullable = false)
    private User streamer;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String thumbnailUrl;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "content_image_urls", joinColumns = @JoinColumn(name = "content_id"))
    @Column(name = "image_url", length = 500)
    @Builder.Default
    private List<String> imageUrls = new ArrayList<>();

    @Column(name = "apply_link")
    private String applyLink;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContentCategory category;

    @ElementCollection(fetch = FetchType.LAZY, targetClass = ContentCategory.class)
    @CollectionTable(name = "content_tags", joinColumns = @JoinColumn(name = "content_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "tag")
    @Builder.Default
    private List<ContentCategory> tags = new ArrayList<>();

    private LocalDateTime startDate;

    private LocalDateTime endDate;

    @Column(columnDefinition = "TEXT")
    private String requirements;

    @Column(columnDefinition = "TEXT")
    private String prize;

    private String recruitCount;

    private Integer followerCount;

    @Builder.Default
    private Boolean followerUnlimited = false;

    private String contactMethod;

    private String contactInfo;

    private String hostName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ContentStatus status = ContentStatus.PENDING;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public void changeStatus(ContentStatus status) {
        this.status = status;
    }

    public void updateFields(
            String title, String description, String thumbnailUrl, List<String> imageUrls,
            String applyLink, ContentCategory category, List<ContentCategory> tags,
            java.time.LocalDateTime startDate, java.time.LocalDateTime endDate,
            String requirements, String prize, String recruitCount,
            Integer followerCount, Boolean followerUnlimited,
            String contactMethod, String contactInfo, String hostName) {
        this.title = title;
        this.description = description;
        this.thumbnailUrl = thumbnailUrl;
        if (imageUrls != null) {
            this.imageUrls.clear();
            this.imageUrls.addAll(imageUrls);
        }
        this.applyLink = applyLink;
        this.category = category;
        if (tags != null) {
            this.tags.clear();
            this.tags.addAll(tags);
        }
        this.startDate = startDate;
        this.endDate = endDate;
        this.requirements = requirements;
        this.prize = prize;
        this.recruitCount = recruitCount;
        this.followerCount = followerCount;
        this.followerUnlimited = followerUnlimited;
        this.contactMethod = contactMethod;
        this.contactInfo = contactInfo;
        this.hostName = hostName;
    }
}
