"""Retrieval quality of embedding models on note-like passages (TASK-096).

Runs against llama-server started with --embedding on 127.0.0.1:$PORT. Prints recall@1, recall@3
and mean reciprocal rank over 20 queries, 5 of them in French, German or Spanish, whose wording
avoids the passage's own words where possible.
"""
import json, math, os, sys, time, urllib.request

PORT = os.environ.get("PORT", "8099")
MODEL = sys.argv[1]

PASSAGES = [
    "The boiler service is booked with Hartley Heating for 14 October at 9am; the engineer needs access to the loft.",
    "Car insurance renews on 2 November. Current premium is 412 pounds with a 250 excess; compare quotes a month before.",
    "Ada's birthday is on 23 March. She likes dark chocolate, jazz records and anything about astronomy.",
    "The Wi-Fi password for the cabin is on a card inside the kitchen drawer, next to the spare keys.",
    "Recipe: slow-cooked beef ragu. Brown the meat, add wine, tomatoes and bay leaves, then cook for four hours.",
    "Passport expires in June next year. Renewal takes about three weeks; photos must be taken without glasses.",
    "The dentist moved to 41 High Street. Check-ups are every six months; the next one is in January.",
    "Project Kestrel launch checklist: freeze the code on Friday, run the migration on Saturday, announce on Monday.",
    "Gym membership costs 32 pounds a month and can be paused for up to three months with a week's notice.",
    "To reset the router, hold the pinhole button for ten seconds until the light flashes orange.",
    "Tomatoes should be watered every morning in summer and fed with seaweed feed once a week.",
    "The mortgage fixed rate ends in April 2027; the broker is Sam Patel, who will call three months before.",
    "Notes from the school meeting: homework club runs Tuesdays and Thursdays until half past four.",
    "Dog vaccinations are due in May; the vet also recommended a tick treatment every three months.",
    "The loft ladder is stiff; spray the hinges with silicone lubricant rather than oil.",
    "Holiday in Lisbon from 10 to 17 August. Flights leave at 06:40 from Manchester; hotel is near Alfama.",
    "Tax return deadline is 31 January. Keep receipts for home office equipment and the new laptop.",
    "Bike tyre pressure should be 80 psi on the road bike and 35 psi on the mountain bike.",
    "Grandma's apple crumble: equal weights of butter, flour and sugar for the topping, bake 40 minutes.",
    "The electricity meter reading on 1 September was 45120; the supplier wants readings every quarter.",
    "Ideas for the garden: a small pond by the fence, a bench under the pear tree, and more lavender.",
    "Laptop backup runs every Sunday night to the external drive labelled BLUE; keep it unplugged otherwise.",
    "The plumber said the kitchen tap washer is worn and quoted 60 pounds to replace the whole cartridge.",
    "Book club is reading The Remains of the Day this month; we meet at Priya's on the last Thursday.",
    "Blood pressure readings: morning average 128 over 82 this week, a little lower than last month.",
    "Parking permit for the street must be renewed online every year before the end of February.",
    "The bread maker needs 500 grams of strong flour, 7 grams of yeast and 320 millilitres of water.",
    "Moving checklist: redirect post, update the electoral roll, tell the bank and the doctor's surgery.",
    "Solar panels produced 3100 kilowatt hours last year; the inverter warranty lasts until 2031.",
    "Our favourite restaurant is Casa Nonna; booking is essential on Fridays and they close on Mondays.",
]
QUERIES = [
    ("When is someone coming to look at the heating?", 0),
    ("How much does my car cover cost each year?", 1),
    ("What present could I buy Ada?", 2),
    ("Where can I find the internet code at the holiday house?", 3),
    ("How do I make the Italian meat sauce?", 4),
    ("How long does it take to get a new travel document?", 5),
    ("What should I do when the internet box stops working?", 9),
    ("How often do the plants in the greenhouse need water?", 10),
    ("Who handles our home loan?", 11),
    ("When do the pets need their jabs?", 13),
    ("What time is the early flight to Portugal?", 15),
    ("What can I claim for working from home?", 16),
    ("How hard should I pump up my bicycle wheels?", 17),
    ("Which drive has my computer backups?", 21),
    ("What did the tradesman say about the leaking faucet?", 22),
    ("Quand est l'anniversaire d'Ada ?", 2),
    ("Wie setze ich den Router zurück?", 9),
    ("¿Cuándo vence el pasaporte?", 5),
    ("Welche Zutaten braucht das Brot?", 26),
    ("Quel restaurant faut-il réserver le vendredi ?", 29),
]
PREFIX = {
    "nomic": ("search_query: ", "search_document: "),
    "bge": ("Represent this sentence for searching relevant passages: ", ""),
    "embeddinggemma": ("task: search result | query: ", "title: none | text: "),
    "Qwen3-Embedding": ("Instruct: Given a question, retrieve the note that answers it\nQuery: ", ""),
}


def prefixes():
    for key, value in PREFIX.items():
        if key in MODEL:
            return value
    return ("", "")


def embed(texts):
    body = json.dumps({"input": texts, "model": "m"}).encode()
    request = urllib.request.Request(f"http://127.0.0.1:{PORT}/v1/embeddings", body, {"Content-Type": "application/json"})
    with urllib.request.urlopen(request, timeout=600) as response:
        return [item["embedding"] for item in json.load(response)["data"]]


def cosine(a, b):
    return sum(x * y for x, y in zip(a, b)) / (math.sqrt(sum(x * x for x in a)) * math.sqrt(sum(y * y for y in b)))


query_prefix, document_prefix = prefixes()
start = time.time()
documents = embed([document_prefix + p for p in PASSAGES])
documents_seconds = time.time() - start
queries = embed([query_prefix + q for q, _ in QUERIES])
ranks = []
for vector, (_, target) in zip(queries, QUERIES):
    order = sorted(range(len(documents)), key=lambda i: -cosine(vector, documents[i]))
    ranks.append(order.index(target) + 1)
english, other = ranks[:15], ranks[15:]
print(json.dumps({
    "model": MODEL,
    "recall@1": sum(r == 1 for r in ranks) / len(ranks),
    "recall@3": sum(r <= 3 for r in ranks) / len(ranks),
    "mrr": round(sum(1 / r for r in ranks) / len(ranks), 3),
    "english_recall@1": sum(r == 1 for r in english) / len(english),
    "other_language_recall@1": sum(r == 1 for r in other) / len(other),
    "dimensions": len(documents[0]),
    "seconds_for_30_passages": round(documents_seconds, 2),
}))
