package com.blog.site;

import com.blog.media.MediaAsset;
import com.blog.shared.persistence.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "site_profile")
public class SiteProfile extends AuditedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "site_name", nullable = false, length = 160)
    private String siteName;

    @Column(nullable = false, length = 160)
    private String subtitle;

    @Column(name = "site_description")
    private String siteDescription;

    @Column(name = "owner_name", nullable = false, length = 120)
    private String ownerName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "avatar_media_id")
    private MediaAsset avatarMedia;

    @Column(name = "github_url", length = 500)
    private String githubUrl;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSiteName() { return siteName; }
    public void setSiteName(String siteName) { this.siteName = siteName; }
    public String getSubtitle() { return subtitle; }
    public void setSubtitle(String subtitle) { this.subtitle = subtitle; }
    public String getSiteDescription() { return siteDescription; }
    public void setSiteDescription(String siteDescription) { this.siteDescription = siteDescription; }
    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
    public MediaAsset getAvatarMedia() { return avatarMedia; }
    public void setAvatarMedia(MediaAsset avatarMedia) { this.avatarMedia = avatarMedia; }
    public String getGithubUrl() { return githubUrl; }
    public void setGithubUrl(String githubUrl) { this.githubUrl = githubUrl; }
}
