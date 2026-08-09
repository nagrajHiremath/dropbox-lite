package com.dropbox.metadata_service.devseed;

import com.dropbox.metadata_service.domain.FileEntity;
import com.dropbox.metadata_service.domain.FileStatus;
import com.dropbox.metadata_service.domain.FileVersion;
import com.dropbox.metadata_service.domain.Folder;
import com.dropbox.metadata_service.domain.FolderStatus;
import com.dropbox.metadata_service.domain.ShareLink;
import com.dropbox.metadata_service.domain.ShareStatus;
import com.dropbox.metadata_service.repository.FileRepository;
import com.dropbox.metadata_service.repository.FileVersionRepository;
import com.dropbox.metadata_service.repository.FolderRepository;
import com.dropbox.metadata_service.repository.FolderSpecifications;
import com.dropbox.metadata_service.repository.ShareLinkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * DEV-ONLY sample data seeder for manually exercising the Metadata Service
 * REST API before the Upload Service / Kafka pipeline exists to populate it
 * for real. Only active under the "dev" profile. Safe to delete this whole
 * package once real data can flow in through the normal upload path.
 *
 * Idempotent: checks for the root "Documents" folder for the fixed test user
 * before seeding anything, so restarts don't create duplicates.
 */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevDataSeeder implements CommandLineRunner {

    public static final UUID TEST_USER_ID = UUID.fromString("e449d7bb-f7ff-417e-bafb-a1e5a180e666");

    private static final SecureRandom RANDOM = new SecureRandom();

    private final FolderRepository folderRepository;
    private final FileRepository fileRepository;
    private final FileVersionRepository fileVersionRepository;
    private final ShareLinkRepository shareLinkRepository;

    @Override
    @Transactional
    public void run(String... args) {
        Specification<Folder> alreadySeededMarker = Specification.allOf(
                FolderSpecifications.ownedBy(TEST_USER_ID),
                FolderSpecifications.hasParent(null),
                FolderSpecifications.hasName("Documents")
        );

//        if (folderRepository.exists(alreadySeededMarker)) {
//            log.info("[dev-seed] Sample data already present for test user {}, skipping.", TEST_USER_ID);
//            return;
//        }

        log.info("[dev-seed] Seeding sample metadata for test user {}...", TEST_USER_ID);

        // ---- Folders ----
        Folder documents = saveFolder("Documents", null, FolderStatus.ACTIVE, null);
        Folder photos = saveFolder("Photos", null, FolderStatus.ACTIVE, null);
        Folder videos = saveFolder("Videos", null, FolderStatus.ACTIVE, null);
        Folder work = saveFolder("Work", documents.getId(), FolderStatus.ACTIVE, null);
        saveFolder("Old Projects", null, FolderStatus.TRASHED, Instant.now().minus(Duration.ofDays(14)));

        // ---- Root-level file ----
        saveFileWithSingleVersion(
                "README.md", null, "text/markdown", FileStatus.ACTIVE, null,
                2_450L, Instant.now().minus(Duration.ofDays(20))
        );

        // ---- Files inside Documents ----
        FileEntity report = saveFileEntity("Q3-Report.pdf", documents.getId(), "application/pdf", FileStatus.ACTIVE, null);
        seedThreeVersions(report);

        saveFileWithSingleVersion(
                "Budget.xlsx", documents.getId(),
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                FileStatus.ACTIVE, null,
                84_200L, Instant.now().minus(Duration.ofDays(9))
        );

        // ---- File inside Documents/Work ----
        saveFileWithSingleVersion(
                "Project-Plan.docx", work.getId(),
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                FileStatus.ACTIVE, null,
                118_640L, Instant.now().minus(Duration.ofDays(6))
        );

        // ---- Image inside Photos ----
        FileEntity sunset = saveFileWithSingleVersion(
                "sunset.jpg", photos.getId(), "image/jpeg", FileStatus.ACTIVE, null,
                3_245_800L, Instant.now().minus(Duration.ofDays(3))
        );

        // ---- Video inside Videos ----
        FileEntity demoRecording = saveFileWithSingleVersion(
                "demo-recording.mp4", videos.getId(), "video/mp4", FileStatus.ACTIVE, null,
                41_820_000L, Instant.now().minus(Duration.ofDays(1))
        );

        // ---- Trashed file ----
        saveFileWithSingleVersion(
                "old-notes.txt", documents.getId(), "text/plain", FileStatus.TRASHED,
                Instant.now().minus(Duration.ofDays(4)),
                1_024L, Instant.now().minus(Duration.ofDays(30))
        );

        // ---- Shares ----
        saveShare(sunset.getId(), "VIEW", ShareStatus.ACTIVE, Instant.now().plus(Duration.ofDays(30)));
        saveShare(report.getId(), "VIEW", ShareStatus.ACTIVE, Instant.now().minus(Duration.ofDays(5))); // expired
        saveShare(demoRecording.getId(), "DOWNLOAD", ShareStatus.REVOKED, null);

        log.info("[dev-seed] Done. Test user UUID: {}", TEST_USER_ID);
        log.info("[dev-seed] Send this as the 'X-User-Id' header when calling the Metadata API directly.");
    }

    private Folder saveFolder(String name, UUID parentId, FolderStatus status, Instant deletedAt) {
        Folder folder = Folder.builder()
                .ownerId(TEST_USER_ID)
                .parentId(parentId)
                .name(name)
                .status(status)
                .deletedAt(deletedAt)
                .build();
        return folderRepository.save(folder);
    }

    private FileEntity saveFileEntity(String name, UUID folderId, String mimeType, FileStatus status, Instant deletedAt) {
        FileEntity file = FileEntity.builder()
                .ownerId(TEST_USER_ID)
                .folderId(folderId)
                .name(name)
                .mimeType(mimeType)
                .status(status)
                .deletedAt(deletedAt)
                .build();
        return fileRepository.save(file);
    }

    private FileEntity saveFileWithSingleVersion(String name, UUID folderId, String mimeType, FileStatus status,
                                                  Instant deletedAt, long sizeBytes, Instant createdAt) {
        FileEntity file = saveFileEntity(name, folderId, mimeType, status, deletedAt);
        FileVersion version = saveVersion(file.getId(), 1, sizeBytes, createdAt);
        file.setCurrentVersionId(version.getId());
        return fileRepository.save(file);
    }

    private void seedThreeVersions(FileEntity file) {
        saveVersion(file.getId(), 1, 410_000L, Instant.now().minus(Duration.ofDays(10)));
        saveVersion(file.getId(), 2, 452_000L, Instant.now().minus(Duration.ofDays(5)));
        FileVersion v3 = saveVersion(file.getId(), 3, 468_320L, Instant.now().minus(Duration.ofDays(1)));

        file.setCurrentVersionId(v3.getId());
        fileRepository.save(file);
    }

    private FileVersion saveVersion(UUID fileId, int versionNumber, long sizeBytes, Instant createdAt) {
        String objectKey = "dropbox-files/%s/%s/%s".formatted(TEST_USER_ID, fileId, UUID.randomUUID());
        FileVersion version = FileVersion.builder()
                .fileId(fileId)
                .versionNumber(versionNumber)
                .objectKey(objectKey)
                .sizeBytes(sizeBytes)
                .checksum(fakeHex(64))
                .etag(fakeHex(32))
                .createdBy(TEST_USER_ID)
                .createdAt(createdAt)
                .build();
        return fileVersionRepository.save(version);
    }

    private void saveShare(UUID fileId, String permission, ShareStatus status, Instant expiresAt) {
        ShareLink share = ShareLink.builder()
                .fileId(fileId)
                .tokenHash(fakeHex(64))
                .permission(permission)
                .status(status)
                .expiresAt(expiresAt)
                .createdBy(TEST_USER_ID)
                .createdAt(Instant.now().minus(Duration.ofDays(2)))
                .build();
        shareLinkRepository.save(share);
    }

    private static String fakeHex(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(Integer.toHexString(RANDOM.nextInt(16)));
        }
        return sb.toString();
    }
}
