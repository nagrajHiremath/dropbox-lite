# Rate Limiting

## Algorithm

`sliding-lua` — same for every route.

## Limits (per second)

| Route | Path | Limit | Key |
|---|---|---|---|
| `account-service` | `/api/v1/auth/**`, `/api/v1/users/**` | 5 | IP |
| `upload-new-version` | `/api/v1/files/*/uploads` | 10 | user |
| `upload-service` | `/api/v1/uploads/**` | 10 | user |
| `download-file-content` | `/api/v1/files/*/content`, `.../versions/*/content` | 20 | user |
| `metadata-service` | `/api/v1/files/**`, `/api/v1/folders/**`, `/api/v1/shares/**` | 40 | user |
| `public-share-content` | `/api/v1/public/shares/*/content` | 5 | IP |
| `public-share-metadata` | `/api/v1/public/shares/**` | 5 | IP |
