-- Migration: Add Telegram Bot schema, indexing, and tables
-- Date: 2026-08-03

-- 1. Ensure 'users' table has telegram_id column and fast index
ALTER TABLE IF EXISTS public.users ADD COLUMN IF NOT EXISTS telegram_id TEXT UNIQUE;
CREATE INDEX IF NOT EXISTS idx_users_telegram_id ON public.users(telegram_id);

-- 2. Create 'books' table if not already existing
CREATE TABLE IF NOT EXISTS public.books (
    id TEXT PRIMARY KEY,
    "bookName" TEXT NOT NULL,
    "className" TEXT,
    subject TEXT,
    description TEXT,
    "coverImage" TEXT,
    "pdfUrl" TEXT,
    "createdAt" BIGINT DEFAULT extract(epoch from now()) * 1000
);

-- Index for title, subject, and class searches
CREATE INDEX IF NOT EXISTS idx_books_search ON public.books("bookName", subject, "className");

-- 3. Create 'videos' table if not already existing
CREATE TABLE IF NOT EXISTS public.videos (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    description TEXT,
    "className" TEXT,
    subject TEXT,
    thumbnail TEXT,
    "videoUrl" TEXT,
    "youtubeVideoId" TEXT,
    chapter TEXT,
    "partNumber" INT DEFAULT 1,
    teacher TEXT,
    duration TEXT,
    "order" INT DEFAULT 0,
    "createdAt" BIGINT DEFAULT extract(epoch from now()) * 1000
);

-- Index for video searches
CREATE INDEX IF NOT EXISTS idx_videos_search ON public.videos(title, subject, chapter);

-- 4. Create 'courses' table if not already existing
CREATE TABLE IF NOT EXISTS public.courses (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    name TEXT,
    description TEXT,
    subject TEXT,
    "className" TEXT,
    instructor TEXT,
    thumbnail TEXT,
    "createdAt" BIGINT DEFAULT extract(epoch from now()) * 1000
);

-- Index for course searches
CREATE INDEX IF NOT EXISTS idx_courses_search ON public.courses(title, subject, "className");

-- 5. Insert Sample Data for books, videos, and courses if empty
INSERT INTO public.books (id, "bookName", "className", subject, description, "pdfUrl")
VALUES 
  ('book_1', 'NCERT Class 10 Physics', 'Class 10', 'Physics', 'Complete textbook covering Light, Electricity & Magnetism', 'https://ncert.nic.in/textbook.php'),
  ('book_2', 'Organic Chemistry Guide', 'Class 12', 'Chemistry', 'Comprehensive reaction mechanisms & practice problems', 'https://ncert.nic.in/textbook.php'),
  ('book_3', 'Mathematics Formulas & Theorems', 'Class 10', 'Mathematics', 'Quick revision handbook for algebra, trigonometry & calculus', 'https://ncert.nic.in/textbook.php')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.videos (id, title, subject, "className", teacher, "youtubeVideoId")
VALUES 
  ('vid_1', 'Light - Reflection and Refraction Full Chapter', 'Physics', 'Class 10', 'Dr. Sharma', 'dQw4w9WgXcQ'),
  ('vid_2', 'Chemical Reactions and Equations - One Shot', 'Chemistry', 'Class 10', 'Prof. Verma', 'dQw4w9WgXcQ'),
  ('vid_3', 'Calculus Integration Techniques Masterclass', 'Mathematics', 'Class 12', 'Anand Sir', 'dQw4w9WgXcQ')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.courses (id, title, subject, "className", instructor, description)
VALUES 
  ('crs_1', 'Class 10 Board Exam Complete Revision', 'All Subjects', 'Class 10', 'Aura Expert Faculty', 'Complete 30-day crash course covering Physics, Chem, Math & Bio'),
  ('crs_2', 'Class 12 Physics Mastery Course', 'Physics', 'Class 12', 'Aura Physics Team', 'Interactive video lectures, numerical solving sessions and chapter notes')
ON CONFLICT (id) DO NOTHING;

-- Enable Row Level Security (RLS) on tables for secure client access while Edge Function accesses via Service Role
ALTER TABLE public.books ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.videos ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.courses ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.users ENABLE ROW LEVEL SECURITY;

-- Grant public read access to books, videos, and courses
DROP POLICY IF EXISTS "Public Read Books" ON public.books;
CREATE POLICY "Public Read Books" ON public.books FOR SELECT USING (true);

DROP POLICY IF EXISTS "Public Read Videos" ON public.videos;
CREATE POLICY "Public Read Videos" ON public.videos FOR SELECT USING (true);

DROP POLICY IF EXISTS "Public Read Courses" ON public.courses;
CREATE POLICY "Public Read Courses" ON public.courses FOR SELECT USING (true);
