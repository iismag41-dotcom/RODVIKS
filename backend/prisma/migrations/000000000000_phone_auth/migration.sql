-- Phone + password auth: optional email, unique phone for login

-- Make email optional (phone-only accounts have no email).
-- Existing rows are untouched; the column stays unique.
ALTER TABLE "User" ALTER COLUMN "email" DROP NOT NULL;

-- Add phone for phone-based auth. Nullable: existing accounts keep NULL.
-- ADD COLUMN does not rewrite the table; no data is touched.
ALTER TABLE "User" ADD COLUMN "phone" TEXT;

-- CreateIndex
CREATE UNIQUE INDEX "User_phone_key" ON "User"("phone");
