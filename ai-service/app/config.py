import os

from dotenv import load_dotenv

load_dotenv()


class Settings:
    gemini_api_key: str = os.getenv("GEMINI_API_KEY", "")
    vision_model: str = os.getenv("AI_VISION_MODEL", "gemini-2.5-flash")
    text_model: str = os.getenv("AI_TEXT_MODEL", "gemini-2.5-flash")


settings = Settings()
