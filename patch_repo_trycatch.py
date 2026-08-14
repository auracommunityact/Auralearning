import re
with open("app/src/main/java/com/example/data/repository/ChatRepository.kt", "r") as f:
    c = f.read()

# For functions that return List or nullable, we can try to wrap.
# Alternatively, I can just catch it in the caller. Let's see who calls ChatRepository.
