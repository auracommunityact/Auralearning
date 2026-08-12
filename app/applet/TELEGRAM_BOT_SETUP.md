# 🤖 Aura Learning Telegram Bot — Supabase Edge Function Setup & Deployment Guide

This directory contains the production-ready **Telegram Bot** for **Aura Learning**, implemented using **Supabase Edge Functions** (Deno) and connected to your Supabase PostgreSQL database.

---

## 📋 Features & Handlers

The Telegram Bot interacts directly with your existing Supabase tables (`books`, `videos`, `courses`, `users`) in real time:

- 🟢 **Auto-Registration**: Automatically saves new Telegram users to the `users` table with their `telegram_id`, name, and study streak.
- 📚 **`/books`**: Browse textbooks, subjects, class levels, and direct PDF download links.
- 🎬 **`/videos`**: Stream video lectures with direct video/YouTube links and chapter details.
- 🎓 **`/courses`**: Browse featured certified learning courses and syllabus details.
- 🔍 **`/search <query>`**: Instant real-time search across `books`, `courses`, and `videos` simultaneously.
- 👤 **`/profile`**: Display real-time student study streak, XP points, grade, and account status.
- 📥 **`/download`**: Quick access to downloadable study materials and PDFs.
- ❓ **`/help`**: View interactive cheat-sheet and command guidance.
- 🔘 **Inline Keyboard Buttons**: Navigate seamlessly with intuitive button grid menus.

---

## 🛠️ Environment Variables Required

Configure the following secrets in your Supabase Dashboard (**Project Settings > Edge Functions > Secrets** or via `supabase secrets set`):

| Variable | Description |
| :--- | :--- |
| `TELEGRAM_BOT_TOKEN` | Token obtained from [@BotFather](https://t.me/BotFather) on Telegram. |
| `SUPABASE_URL` | Your Supabase project URL (e.g. `https://your-project.supabase.co`). |
| `SUPABASE_SERVICE_ROLE_KEY` | Your Supabase `service_role` secret key for server-side database access. |

---

## 🚀 Deployment Instructions

### Step 1: Execute SQL Database Migration
In your **Supabase Dashboard > SQL Editor**, run the contents of `supabase/migrations/20260803_telegram_bot_schema.sql` to ensure all necessary tables and indexes exist.

---

### Step 2: Set Edge Function Secrets
Run the following commands using the Supabase CLI (or set them in the web dashboard):

```bash
supabase secrets set TELEGRAM_BOT_TOKEN="your_telegram_bot_token_here"
supabase secrets set SUPABASE_URL="https://your-project.supabase.co"
supabase secrets set SUPABASE_SERVICE_ROLE_KEY="your_service_role_key_here"
```

---

### Step 3: Deploy Edge Function to Supabase
Deploy the function using the Supabase CLI:

```bash
supabase functions deploy telegram-bot --no-verify-jwt
```

Your Edge Function URL will be:
`https://<your-project-ref>.supabase.co/functions/v1/telegram-bot`

---

### Step 4: Register Webhook with Telegram API
Link your Telegram Bot to your newly deployed Supabase Edge Function by issuing a simple `curl` request:

```bash
curl -X POST "https://api.telegram.org/bot<YOUR_TELEGRAM_BOT_TOKEN>/setWebhook" \
  -H "Content-Type: application/json" \
  -d '{"url": "https://<YOUR_PROJECT_REF>.supabase.co/functions/v1/telegram-bot"}'
```

#### Expected Telegram Response:
```json
{
  "ok": true,
  "result": true,
  "description": "Webhook was set"
}
```

---

## 🧪 Testing Your Telegram Bot

Open your Telegram app, search for your bot username, and test the commands:

1. Send `/start` — Verifies welcome screen and inline navigation buttons.
2. Send `/books` — Fetches textbook records from Supabase `books` table.
3. Send `/videos` — Fetches video records from Supabase `videos` table.
4. Send `/courses` — Fetches course records from Supabase `courses` table.
5. Send `/search Physics` — Executes real-time search across all tables.
6. Send `/profile` — Verifies user auto-registration in `users` table.
7. Send `/download` — Fetches downloadable PDF resources.

---

## 🔒 Security Best Practices

- The function uses `SUPABASE_SERVICE_ROLE_KEY` securely on the server side to perform user registration and reads.
- `no-verify-jwt` flag is set during deployment because Telegram sends requests directly to the webhook URL without a standard Supabase user JWT header.
- Input values in user responses are sanitised using `escapeHtml()` to prevent injection in Telegram HTML parse mode.
