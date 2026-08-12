import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

// Initialize environment secrets
const SUPABASE_URL = Deno.env.get("SUPABASE_URL") || "";
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || "";
const TELEGRAM_BOT_TOKEN = Deno.env.get("TELEGRAM_BOT_TOKEN") || "";

// Initialize Supabase Client with service role permissions for administrative Edge ops
const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY);

// Helper function to send requests to Telegram Bot API
async function callTelegram(method: string, payload: Record<string, unknown>) {
  if (!TELEGRAM_BOT_TOKEN) {
    console.error("Missing TELEGRAM_BOT_TOKEN environment variable.");
    return null;
  }
  const url = `https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/${method}`;
  try {
    const res = await fetch(url, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });
    const data = await res.json();
    if (!data.ok) {
      console.error(`Telegram API Call Failed [${method}]:`, data);
    }
    return data;
  } catch (err) {
    console.error(`Network error calling Telegram API [${method}]:`, err);
    return null;
  }
}

// Answer Callback Query to dismiss Telegram button loading spinner
async function answerCallbackQuery(callbackQueryId: string, text?: string) {
  await callTelegram("answerCallbackQuery", {
    callback_query_id: callbackQueryId,
    text: text || "",
  });
}

// Utility to escape HTML characters in dynamic strings
function escapeHtml(str: string = ""): string {
  return str
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

// Auto-register or retrieve user in Supabase 'users' table
async function ensureUserRegistered(tgUser: { id: number; first_name: string; last_name?: string; username?: string }) {
  if (!tgUser) return null;
  const telegramIdStr = tgUser.id.toString();
  const fullName = [tgUser.first_name, tgUser.last_name].filter(Boolean).join(" ");

  try {
    // 1. Query existing user by telegram_id
    const { data: existingUser, error: findError } = await supabase
      .from("users")
      .select("*")
      .eq("telegram_id", telegramIdStr)
      .maybeSingle();

    if (existingUser) {
      return existingUser;
    }

    // 2. Register new user if not found
    const newUserRecord = {
      id: `tg_${telegramIdStr}`,
      telegram_id: telegramIdStr,
      name: fullName || tgUser.username || `Student_${telegramIdStr.slice(-4)}`,
      email: tgUser.username ? `${tgUser.username}@telegram.user` : `tg_${telegramIdStr}@telegram.user`,
      role: "student",
      points: 100,
      studyStreak: 1,
      selectedGrade: "Class 10",
      accountStatus: "Active",
      createdAt: Date.now(),
    };

    const { data: createdUser, error: insertError } = await supabase
      .from("users")
      .insert([newUserRecord])
      .select()
      .maybeSingle();

    if (insertError) {
      console.warn("User insert notice (falling back to generated record):", insertError.message);
      return newUserRecord;
    }

    return createdUser || newUserRecord;
  } catch (err) {
    console.error("User registration error:", err);
    return null;
  }
}

// Main Menu Inline Keyboard Component
function getMainMenuKeyboard() {
  return {
    inline_keyboard: [
      [
        { text: "📚 Browse Books", callback_data: "cmd_books" },
        { text: "🎬 Video Lectures", callback_data: "cmd_videos" },
      ],
      [
        { text: "🎓 Explore Courses", callback_data: "cmd_courses" },
        { text: "📥 Quick Downloads", callback_data: "cmd_download" },
      ],
      [
        { text: "🔍 Search Library", callback_data: "cmd_search_prompt" },
        { text: "👤 My Profile", callback_data: "cmd_profile" },
      ],
      [
        { text: "❓ Help & Commands", callback_data: "cmd_help" }
      ]
    ]
  };
}

// Search Prompt Inline Keyboard
function getSearchKeyboard() {
  return {
    inline_keyboard: [
      [
        { text: "⚛️ Physics", callback_data: "search_Physics" },
        { text: "🧪 Chemistry", callback_data: "search_Chemistry" },
      ],
      [
        { text: "📐 Mathematics", callback_data: "search_Mathematics" },
        { text: "🧬 Biology", callback_data: "search_Biology" },
      ],
      [
        { text: "🏫 Class 10", callback_data: "search_Class 10" },
        { text: "🏫 Class 12", callback_data: "search_Class 12" },
      ],
      [
        { text: "🔙 Main Menu", callback_data: "cmd_start" }
      ]
    ]
  };
}

// ---------------- COMMAND HANDLERS ---------------- //

async function handleStartCommand(chatId: number, user: any) {
  const userName = user?.name ? escapeHtml(user.name) : "Learner";
  const text = `🌟 <b>Welcome to Aura Learning AI Bot, ${userName}!</b>\n\n` +
    `Your direct portal to <b>Aura Learning Hub</b> — offering free access to textbooks, video lessons, courses, and educational tools.\n\n` +
    `✨ <i>What would you like to explore today? Select an option below or type <code>/search &lt;topic&gt;</code> to begin!</i>`;

  await callTelegram("sendMessage", {
    chat_id: chatId,
    text: text,
    parse_mode: "HTML",
    reply_markup: getMainMenuKeyboard(),
  });
}

async function handleHelpCommand(chatId: number) {
  const text = `📖 <b>Aura Learning Bot — User Guide & Commands</b>\n\n` +
    `Here are all available commands you can use anytime:\n\n` +
    `🔹 <b>/start</b> - Launch interactive main dashboard\n` +
    `🔹 <b>/books</b> - Browse textbook library & downloadable PDFs\n` +
    `🔹 <b>/videos</b> - Stream subject video lectures\n` +
    `🔹 <b>/courses</b> - Explore structured learning courses\n` +
    `🔹 <b>/search &lt;query&gt;</b> - Instant search across books, courses & videos\n` +
    `🔹 <b>/profile</b> - View your learning streak, points & account status\n` +
    `🔹 <b>/download</b> - Direct access to study materials & PDF downloads\n` +
    `🔹 <b>/help</b> - Display this instruction manual\n\n` +
    `💡 <b>Pro Tip:</b> You can type <code>/search Physics</code> or <code>/search Class 10</code> directly into the chat!`;

  await callTelegram("sendMessage", {
    chat_id: chatId,
    text: text,
    parse_mode: "HTML",
    reply_markup: {
      inline_keyboard: [
        [{ text: "🔍 Try Search", callback_data: "cmd_search_prompt" }],
        [{ text: "🔙 Main Menu", callback_data: "cmd_start" }]
      ]
    }
  });
}

async function handleBooksCommand(chatId: number) {
  try {
    const { data: books, error } = await supabase
      .from("books")
      .select("*")
      .order("createdAt", { ascending: false })
      .limit(6);

    let text = `📚 <b>Aura Learning — Books & Textbooks</b>\n\n`;

    if (error || !books || books.length === 0) {
      text += `Currently updating library catalog. Check back soon!`;
    } else {
      books.forEach((b: any, i: number) => {
        const title = escapeHtml(b.bookName || b.title || "Textbook");
        const subject = b.subject ? ` | 📌 ${escapeHtml(b.subject)}` : "";
        const className = b.className ? ` | 🏫 ${escapeHtml(b.className)}` : "";
        const desc = b.description ? `\n   <i>${escapeHtml(b.description.substring(0, 70))}...</i>` : "";
        const pdfLink = b.pdfUrl ? `\n   📥 <a href="${b.pdfUrl}">Download PDF Document</a>` : "";

        text += `<b>${i + 1}. ${title}</b>${subject}${className}${desc}${pdfLink}\n\n`;
      });
    }

    await callTelegram("sendMessage", {
      chat_id: chatId,
      text: text,
      parse_mode: "HTML",
      disable_web_page_preview: true,
      reply_markup: {
        inline_keyboard: [
          [
            { text: "🔍 Search Books", callback_data: "cmd_search_prompt" },
            { text: "📥 Quick Downloads", callback_data: "cmd_download" }
          ],
          [{ text: "🔙 Main Menu", callback_data: "cmd_start" }]
        ]
      }
    });
  } catch (err) {
    console.error("Error handling /books:", err);
    await callTelegram("sendMessage", { chatId, text: "Unable to retrieve books at this moment." });
  }
}

async function handleVideosCommand(chatId: number) {
  try {
    const { data: videos, error } = await supabase
      .from("videos")
      .select("*")
      .order("createdAt", { ascending: false })
      .limit(6);

    let text = `🎬 <b>Aura Learning — Video Lectures</b>\n\n`;

    if (error || !videos || videos.length === 0) {
      text += `No video lectures currently indexed in the database.`;
    } else {
      videos.forEach((v: any, i: number) => {
        const title = escapeHtml(v.title || "Video Lesson");
        const teacher = v.teacher ? ` | 👨‍🏫 ${escapeHtml(v.teacher)}` : "";
        const subject = v.subject ? ` | 📌 ${escapeHtml(v.subject)}` : "";
        const videoUrl = v.videoUrl || (v.youtubeVideoId ? `https://www.youtube.com/watch?v=${v.youtubeVideoId}` : "");
        const watchBtn = videoUrl ? `\n   ▶️ <a href="${videoUrl}">Watch Video Lecture</a>` : "";

        text += `<b>${i + 1}. ${title}</b>${teacher}${subject}${watchBtn}\n\n`;
      });
    }

    await callTelegram("sendMessage", {
      chat_id: chatId,
      text: text,
      parse_mode: "HTML",
      disable_web_page_preview: true,
      reply_markup: {
        inline_keyboard: [
          [{ text: "🔙 Main Menu", callback_data: "cmd_start" }]
        ]
      }
    });
  } catch (err) {
    console.error("Error handling /videos:", err);
    await callTelegram("sendMessage", { chatId, text: "Unable to retrieve video lectures at this moment." });
  }
}

async function handleCoursesCommand(chatId: number) {
  try {
    const { data: courses, error } = await supabase
      .from("courses")
      .select("*")
      .limit(6);

    let text = `🎓 <b>Aura Learning — Featured Courses</b>\n\n`;

    if (error || !courses || courses.length === 0) {
      // Fallback sample courses if table empty
      text += `1. <b>Class 10 CBSE Science Complete Course</b>\n   📌 Physics, Chemistry & Biology with animated lessons\n\n`;
      text += `2. <b>Mathematics Mastery - Class 12 Board Prep</b>\n   📌 Calculus, Vectors & Algebra deep dive\n\n`;
      text += `3. <b>English Grammar & Composition Excellence</b>\n   📌 Vocabulary building, writing skills & practice papers\n\n`;
    } else {
      courses.forEach((c: any, i: number) => {
        const title = escapeHtml(c.title || c.name || "Special Course");
        const instructor = c.instructor ? ` | 👨‍🏫 ${escapeHtml(c.instructor)}` : "";
        const desc = c.description ? `\n   <i>${escapeHtml(c.description.substring(0, 80))}...</i>` : "";
        text += `<b>${i + 1}. ${title}</b>${instructor}${desc}\n\n`;
      });
    }

    await callTelegram("sendMessage", {
      chat_id: chatId,
      text: text,
      parse_mode: "HTML",
      reply_markup: {
        inline_keyboard: [
          [
            { text: "🔍 Search Courses", callback_data: "cmd_search_prompt" },
            { text: "📚 Browse Books", callback_data: "cmd_books" }
          ],
          [{ text: "🔙 Main Menu", callback_data: "cmd_start" }]
        ]
      }
    });
  } catch (err) {
    console.error("Error handling /courses:", err);
    await callTelegram("sendMessage", { chatId, text: "Unable to load courses at this time." });
  }
}

async function handleSearchCommand(chatId: number, query: string) {
  if (!query || query.trim() === "") {
    const promptText = `🔍 <b>Search Aura Learning Database</b>\n\n` +
      `Please provide a keyword to search, e.g.:\n` +
      `• <code>/search Physics</code>\n` +
      `• <code>/search Class 10</code>\n` +
      `• <code>/search Algebra</code>\n\n` +
      `Or tap one of the quick search categories below:`;

    await callTelegram("sendMessage", {
      chat_id: chatId,
      text: promptText,
      parse_mode: "HTML",
      reply_markup: getSearchKeyboard()
    });
    return;
  }

  const cleanQuery = query.trim();
  const ilikePattern = `%${cleanQuery}%`;

  try {
    // Perform concurrent search queries across books, courses, and videos
    const [booksRes, coursesRes, videosRes] = await Promise.all([
      supabase.from("books").select("*").or(`bookName.ilike.${ilikePattern},subject.ilike.${ilikePattern},description.ilike.${ilikePattern}`).limit(4),
      supabase.from("courses").select("*").or(`title.ilike.${ilikePattern},subject.ilike.${ilikePattern},description.ilike.${ilikePattern}`).limit(4),
      supabase.from("videos").select("*").or(`title.ilike.${ilikePattern},chapter.ilike.${ilikePattern},subject.ilike.${ilikePattern}`).limit(4)
    ]);

    let resultsText = `🔍 <b>Real-Time Search Results for:</b> "<code>${escapeHtml(cleanQuery)}</code>"\n\n`;

    let totalFound = 0;

    // Books section
    if (booksRes.data && booksRes.data.length > 0) {
      resultsText += `📚 <b>Books (${booksRes.data.length}):</b>\n`;
      booksRes.data.forEach((b: any) => {
        totalFound++;
        const title = escapeHtml(b.bookName || b.title || "Book");
        const link = b.pdfUrl ? ` — <a href="${b.pdfUrl}">Download PDF</a>` : "";
        resultsText += `• <b>${title}</b> (${escapeHtml(b.subject || "General")})${link}\n`;
      });
      resultsText += `\n`;
    }

    // Courses section
    if (coursesRes.data && coursesRes.data.length > 0) {
      resultsText += `🎓 <b>Courses (${coursesRes.data.length}):</b>\n`;
      coursesRes.data.forEach((c: any) => {
        totalFound++;
        resultsText += `• <b>${escapeHtml(c.title || c.name)}</b> (${escapeHtml(c.subject || "Course")})\n`;
      });
      resultsText += `\n`;
    }

    // Videos section
    if (videosRes.data && videosRes.data.length > 0) {
      resultsText += `🎬 <b>Video Lessons (${videosRes.data.length}):</b>\n`;
      videosRes.data.forEach((v: any) => {
        totalFound++;
        const vUrl = v.videoUrl || (v.youtubeVideoId ? `https://www.youtube.com/watch?v=${v.youtubeVideoId}` : "");
        const link = vUrl ? ` — <a href="${vUrl}">Watch Video</a>` : "";
        resultsText += `• <b>${escapeHtml(v.title)}</b> (${escapeHtml(v.subject || "Video")})${link}\n`;
      });
      resultsText += `\n`;
    }

    if (totalFound === 0) {
      resultsText += `No matching study resources found in Supabase for "<b>${escapeHtml(cleanQuery)}</b>".\n\nTry searching for broader terms like <i>Physics</i>, <i>Chemistry</i>, <i>Class 10</i>, or <i>Mathematics</i>.`;
    }

    await callTelegram("sendMessage", {
      chat_id: chatId,
      text: resultsText,
      parse_mode: "HTML",
      disable_web_page_preview: true,
      reply_markup: {
        inline_keyboard: [
          [
            { text: "🔍 Search Again", callback_data: "cmd_search_prompt" },
            { text: "📚 All Books", callback_data: "cmd_books" }
          ],
          [{ text: "🔙 Main Menu", callback_data: "cmd_start" }]
        ]
      }
    });
  } catch (err) {
    console.error("Search execution error:", err);
    await callTelegram("sendMessage", { chatId, text: "Search error occurred. Please try again." });
  }
}

async function handleProfileCommand(chatId: number, userRecord: any, tgUser: any) {
  const telegramId = tgUser?.id || "N/A";
  const name = escapeHtml(userRecord?.name || tgUser?.first_name || "Student");
  const role = escapeHtml(userRecord?.role || "Student");
  const grade = escapeHtml(userRecord?.selectedGrade || "Class 10");
  const points = userRecord?.points ?? 100;
  const streak = userRecord?.studyStreak ?? 1;
  const status = escapeHtml(userRecord?.accountStatus || "Active");

  const text = `👤 <b>Aura Learning Student Profile</b>\n\n` +
    `👤 <b>Name:</b> ${name}\n` +
    `🆔 <b>Telegram ID:</b> <code>${telegramId}</code>\n` +
    `🎓 <b>Grade / Class:</b> ${grade}\n` +
    `🔑 <b>Account Role:</b> ${role}\n` +
    `⭐️ <b>Study Points:</b> ${points} XP\n` +
    `🔥 <b>Study Streak:</b> ${streak} Day(s)\n` +
    `✅ <b>Account Status:</b> ${status}\n\n` +
    `<i>Your progress and preferences sync seamlessly with the Aura Learning Mobile App!</i>`;

  await callTelegram("sendMessage", {
    chat_id: chatId,
    text: text,
    parse_mode: "HTML",
    reply_markup: {
      inline_keyboard: [
        [
          { text: "📚 Browse Books", callback_data: "cmd_books" },
          { text: "🎓 My Courses", callback_data: "cmd_courses" }
        ],
        [{ text: "🔙 Main Menu", callback_data: "cmd_start" }]
      ]
    }
  });
}

async function handleDownloadCommand(chatId: number) {
  try {
    const { data: books } = await supabase
      .from("books")
      .select("id, bookName, subject, className, pdfUrl")
      .not("pdfUrl", "is", null)
      .limit(6);

    let text = `📥 <b>Direct PDF Resource Downloads</b>\n\n`;

    if (!books || books.length === 0) {
      text += `1. 📄 <b>NCERT Class 10 Science Exemplar</b>\n   🔗 <a href="https://ncert.nic.in/textbook.php">Download Official NCERT PDF</a>\n\n`;
      text += `2. 📄 <b>Class 12 Physics Formula Handbook</b>\n   🔗 <a href="https://ncert.nic.in/textbook.php">Download Physics Quick Reference PDF</a>\n\n`;
    } else {
      books.forEach((b: any, i: number) => {
        text += `${i + 1}. 📄 <b>${escapeHtml(b.bookName || "Study Material")}</b>\n`;
        if (b.subject) text += `   📌 Subject: ${escapeHtml(b.subject)}\n`;
        if (b.pdfUrl) text += `   🔗 <a href="${b.pdfUrl}">Direct Download Link</a>\n`;
        text += `\n`;
      });
    }

    await callTelegram("sendMessage", {
      chat_id: chatId,
      text: text,
      parse_mode: "HTML",
      disable_web_page_preview: true,
      reply_markup: {
        inline_keyboard: [
          [{ text: "🔙 Main Menu", callback_data: "cmd_start" }]
        ]
      }
    });
  } catch (err) {
    console.error("Error handling /download:", err);
    await callTelegram("sendMessage", { chatId, text: "Unable to retrieve download list." });
  }
}

// ---------------- MAIN EDGE FUNCTION ROUTER ---------------- //

serve(async (req: Request) => {
  // CORS & Options method preflight handling
  if (req.method === "OPTIONS") {
    return new Response("OK", {
      status: 200,
      headers: {
        "Access-Control-Allow-Origin": "*",
        "Access-Control-Allow-Methods": "POST, GET, OPTIONS",
        "Access-Control-Allow-Headers": "Content-Type, Authorization, x-telegram-bot-api-secret-token",
      },
    });
  }

  // Allow GET request for health check
  if (req.method === "GET") {
    return new Response(
      JSON.stringify({
        status: "online",
        service: "Aura Learning Telegram Bot Supabase Edge Function",
        timestamp: new Date().toISOString(),
      }),
      { headers: { "Content-Type": "application/json" } }
    );
  }

  if (req.method !== "POST") {
    return new Response("Method Not Allowed", { status: 405 });
  }

  try {
    const update = await req.json();

    // 1. Handle Direct Messages
    if (update.message) {
      const msg = update.message;
      const chatId = msg.chat.id;
      const text = msg.text || "";
      const tgUser = msg.from;

      // Auto-register or verify user in Supabase
      const userRecord = await ensureUserRegistered(tgUser);

      if (text.startsWith("/start")) {
        await handleStartCommand(chatId, userRecord);
      } else if (text.startsWith("/help")) {
        await handleHelpCommand(chatId);
      } else if (text.startsWith("/books")) {
        await handleBooksCommand(chatId);
      } else if (text.startsWith("/videos")) {
        await handleVideosCommand(chatId);
      } else if (text.startsWith("/courses")) {
        await handleCoursesCommand(chatId);
      } else if (text.startsWith("/search")) {
        const query = text.replace("/search", "").trim();
        await handleSearchCommand(chatId, query);
      } else if (text.startsWith("/profile")) {
        await handleProfileCommand(chatId, userRecord, tgUser);
      } else if (text.startsWith("/download")) {
        await handleDownloadCommand(chatId);
      } else {
        // Fallback text input: Treat as search query
        await handleSearchCommand(chatId, text);
      }
    }

    // 2. Handle Inline Keyboard Callback Queries
    if (update.callback_query) {
      const callbackQuery = update.callback_query;
      const cbId = callbackQuery.id;
      const chatId = callbackQuery.message.chat.id;
      const data = callbackQuery.data || "";
      const tgUser = callbackQuery.from;

      await answerCallbackQuery(cbId);

      const userRecord = await ensureUserRegistered(tgUser);

      if (data === "cmd_start") {
        await handleStartCommand(chatId, userRecord);
      } else if (data === "cmd_help") {
        await handleHelpCommand(chatId);
      } else if (data === "cmd_books") {
        await handleBooksCommand(chatId);
      } else if (data === "cmd_videos") {
        await handleVideosCommand(chatId);
      } else if (data === "cmd_courses") {
        await handleCoursesCommand(chatId);
      } else if (data === "cmd_search_prompt") {
        await handleSearchCommand(chatId, "");
      } else if (data.startsWith("search_")) {
        const query = data.replace("search_", "");
        await handleSearchCommand(chatId, query);
      } else if (data === "cmd_profile") {
        await handleProfileCommand(chatId, userRecord, tgUser);
      } else if (data === "cmd_download") {
        await handleDownloadCommand(chatId);
      }
    }

    return new Response(JSON.stringify({ ok: true }), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });
  } catch (err) {
    console.error("Unhandled Edge Function error:", err);
    return new Response(JSON.stringify({ error: String(err) }), {
      status: 500,
      headers: { "Content-Type": "application/json" },
    });
  }
});
