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

SYSTEM_PROMPT = (
    "You are an assistant specialized in analyzing hiking trail reviews."
    "Your task is to read a set of reviews and produce a brief, informative summary."
    "The summary must: "
    "- describe the overall opinion of hikers (prevailing sentiment),\n"
    "- collect the most frequent observations (positive and negative),"
    "- note any recurring details (e.g. trail difficulty, conditions, views, crowding, parking, signage, safety, practical tips), and be very concise (maximum 3 sentences)."
    "Do not add personal comments beyond the required summary. Respond exclusively with the summary in English."
)

sample_reviews = [
    "Absolutely stunning views at the summit! The trail was well-marked and not too difficult. Took about 3 hours. Would recommend.",
    "Beautiful hike but parking was a nightmare. Arrived at 9am and the lot was full. The trail itself is moderate, with some steep sections.",
    "Great for families, my kids (8 and 10) handled it fine. Nice shaded areas. The waterfall was a highlight.",
    "Trail was muddy and slippery after recent rain. Proper boots are a must. The scenery is nice but not exceptional.",
    "Loved the wildflowers along the path. Very peaceful on a weekday. Only saw a few other hikers.",
    "Way too crowded on weekends. The view from the top is overrated in my opinion. Not worth the effort if you're looking for solitude.",
]

reviews_block = "\n---\n".join(sample_reviews)
user_message = f"Recensioni:\n\n{reviews_block}"

try:
    response = client.chat.completions.create(
        model="openai/gpt-oss-120b:free",
        messages=[
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": user_message},
        ],
        temperature=0.3,
        max_tokens=300,
    )

    summary = response.choices[0].message.content
    print("\n--- RIASSUNTO GENERATO ---")
    print(summary)
    print("--------------------------\n")

except Exception as e:
    print(f"\nSi è verificato un errore: {e}")