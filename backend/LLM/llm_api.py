from openai import OpenAI

import sys
from pathlib import Path

current_dir = Path(__file__).resolve().parent
project_root = current_dir.parent.parent
sys.path.append(str(project_root))

from api import API_KEY

client = OpenAI(
    base_url="https://openrouter.ai/api/v1", 
    api_key=API_KEY,
)

try:
    response = client.chat.completions.create(
        model= "openai/gpt-oss-120b:free",
        messages=[
            {"role": "system", "content": "Sei sto facendo la mia prima prova con un LLM"},
            {"role": "user", "content": "Ciao! Scrivi una breve frase divertente."}
        ]
    )

    risposta_ai = response.choices[0].message.content
    print("\n--- RISPOSTA DELL'IA ---")
    print(risposta_ai)
    print("------------------------\n")

except Exception as e:
    print(f"\nSi è verificato un errore: {e}")