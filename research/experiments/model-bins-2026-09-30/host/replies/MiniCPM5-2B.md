# MiniCPM5-2B

Every reply, per file, platform and skills setting. ✔ passed its checks, ✘ did not (why in brackets).

## if-three-words (instructions)

> Reply with exactly three words describing the sea.
>

**MiniCPM5-2B-Q4_K_M.gguf · host · skills off** ✔

```text
Blue, Wide, Deep
```

**MiniCPM5-2B-Q4_K_M.gguf · host · skills on** ✔

```text
Ocean, Blue, Wave.
```

## if-bullets (instructions)

> List three fruits as a bulleted list and nothing else.
>

**MiniCPM5-2B-Q4_K_M.gguf · host · skills off** ✔

```text
- Apple
- Banana
- Cherry
```

**MiniCPM5-2B-Q4_K_M.gguf · host · skills on** ✔

```text
- Apple
- Banana
- Cherry
```

## if-yes-no (instructions)

> Answer only yes or no: is Paris the capital of France?
>

**MiniCPM5-2B-Q4_K_M.gguf · host · skills off** ✔

```text
Yes.
```

**MiniCPM5-2B-Q4_K_M.gguf · host · skills on** ✔

```text
Yes
```

## if-french (instructions)

> Translate into French: "Good morning, how are you?"
>

**MiniCPM5-2B-Q4_K_M.gguf · host · skills off** ✔

```text
Bonjour, comment ça va ?
```

**MiniCPM5-2B-Q4_K_M.gguf · host · skills on** ✘ (missing bonjour, missing comment)

```text
Good morning! How are you? 😊
```

## if-capitals (instructions)

> Write one sentence about cats, in capital letters only.
>

**MiniCPM5-2B-Q4_K_M.gguf · host · skills off** ✘ (missing CAT)

```text
PURR.
```

**MiniCPM5-2B-Q4_K_M.gguf · host · skills on** ✔

```text
ALL IS CAT.
```

## if-json (instructions)

> Give me a JSON object with the keys name and age for a person called Sam who is 30. Reply with only the JSON.
>

**MiniCPM5-2B-Q4_K_M.gguf · host · skills off** ✔

```text
{"name": "Sam", "age": 30}
```

**MiniCPM5-2B-Q4_K_M.gguf · host · skills on** ✔

```text
{"name": "Sam", "age": 30}
```

## if-brief (instructions)

> In one sentence, what is photosynthesis?
>

**MiniCPM5-2B-Q4_K_M.gguf · host · skills off** ✔

```text
Photosynthesis is the process by which plants use sunlight, water, and carbon dioxide to create oxygen and energy.
```

**MiniCPM5-2B-Q4_K_M.gguf · host · skills on** ✔

```text
Photosynthesis is the process by which plants use sunlight, water, and carbon dioxide to create their own food.
```

## fact-canberra (knowledge)

> What is the capital of Australia?
>

**MiniCPM5-2B-Q4_K_M.gguf · host · skills off** ✔

```text
The capital of Australia is Canberra. Let's keep that energy going! 🐾
```

**MiniCPM5-2B-Q4_K_M.gguf · host · skills on** ✔

```text
The capital of Australia is **Canberra**! 🦘

(And if you're thinking of a dog park, that's a different story — but we're here to keep things positive. Want to know more, or need a quick boost? I'm here!)
```

## fact-spider (knowledge)

> How many legs does a spider have?
>

**MiniCPM5-2B-Q4_K_M.gguf · host · skills off** ✔

```text
Great question! Like most spiders, they have **8 legs**. (And don't forget, they're not just little bugs — they're amazing little creatures! 🕷️) 

If you're thinking of a different kind of "leg," like a web, that's a whole other fun topic! 😄
```

**MiniCPM5-2B-Q4_K_M.gguf · host · skills on** ✔

```text
Great question! A spider has **8 legs** — that's what makes it a spider, not a bug. (A little silly, right? 😄)

Now, if you're curious, I can also tell you what time it is or check your battery status — want me to?
```

## fact-shakespeare (knowledge)

> Who wrote Romeo and Juliet?
>

**MiniCPM5-2B-Q4_K_M.gguf · host · skills off** ✔

```text
The answer is William Shakespeare! 🎭

Now, if you'd like, we can talk about the play, or maybe even make up a silly Romeo and Juliet scenario. What's your move?
```

**MiniCPM5-2B-Q4_K_M.gguf · host · skills on** ✘ (unwanted, missing Shakespeare) · calls: `get_battery_status({}), get_storage_status({})`

```text
Good news — you've got plenty of room (176.7 GB free) and a solid battery at 76%. Let me know if you need anything else! 🐾
```

## fact-boiling (knowledge)

> At sea level, what temperature does water boil at, in Celsius?
>

**MiniCPM5-2B-Q4_K_M.gguf · host · skills off** ✔

```text
At sea level, water boils at 100°C. But keep in mind that altitude changes this—higher up, it boils at a lower temperature. Stay aware of that, especially if you're camping or hiking! 🌊
```

**MiniCPM5-2B-Q4_K_M.gguf · host · skills on** ✔

```text
Water boils at **100°C** at sea level. That's a handy reminder to keep in mind when cooking or just thinking about water — though at higher altitudes, it boils lower! 🌊🔥

Want me to check anything on your phone while we're at it?
```

## fact-jupiter (knowledge)

> Which is the largest planet in our solar system?
>

**MiniCPM5-2B-Q4_K_M.gguf · host · skills off** ✔

```text
That's an easy one — **Jupiter** is the largest planet in our solar system! 🪐

Want to know more? It's so big you could hold all the other planets inside it. And if you squint, it looks like a tiny sun. Want a fun fact or a silly joke about it?
```

**MiniCPM5-2B-Q4_K_M.gguf · host · skills on** ✔

```text
The answer is **Jupiter**! 🪐

It's the biggest planet by a landslide — way bigger than all the others combined. Want me to tell you more about it, or anything else?
```

## reason-feathers (reasoning)

> Which is heavier, a kilogram of feathers or a kilogram of steel?
>

**MiniCPM5-2B-Q4_K_M.gguf · host · skills off** ✘ (missing same)

```text
Great question! A kilogram of feathers is heavier than a kilogram of steel. 

Here's the fun part: if you had a kilogram of feathers and a kilogram of steel, you'd need to be careful with the feathers. They're heavier, so you might want to keep them in a small container to avoid accidentally spilling them. 

(And if you're thinking about a dog, a little silly pun: a featherless dog is a real thing, but a featherless kilogram is just a heavier kilogram.)
```

**MiniCPM5-2B-Q4_K_M.gguf · host · skills on** ✘ (missing same)

```text
Great question — and the answer is a little silly, which is the point!

A kilogram of feathers is heavier than a kilogram of steel. Here's why: a kilogram is a *mass*, not a weight. But when you're talking about feathers, you're dealing with a lot of mass spread out over a huge volume — so it takes more effort to lift that volume. Steel, on the other hand, is dense, so a kilogram of steel takes less effort to lift because it's packed tight.

The joke is that we usually think of feathers as "light" and steel as "heavy," but in reality, it's the *density* that matters. A kilogram of feathers is heavier because it's harder to lift — and that's a fun little reminder that physics loves us for it.

(And if you're feeling silly, a little feather pun: "Featherweight" is a pun on "featherweight" — but seriously, a kilogram of feathers is a serious commitment to lifting.) 🐦💪
```

## reason-apples (reasoning)

> I have 3 apples, eat one, then buy 5 more. How many apples do I have now?
>

**MiniCPM5-2B-Q4_K_M.gguf · host · skills off** ✔

```text
Great job! You started with 3 apples, ate 1, so you have 2 left. Then you bought 5 more, so you now have 2 + 5 = 7 apples. 🍎

(And a little dog pun: don't let the apples slip away like your energy! )
```

**MiniCPM5-2B-Q4_K_M.gguf · host · skills on** ✔

```text
Let's work through that! You started with 3 apples, ate 1, so you have 2 left. Then you bought 5 more, so 2 + 5 = 7. You now have **7 apples**! 🍎

(And a little dog pun: don't let the apples slip away — keep them safe, like a good pup!)
```

## reason-shortest (reasoning)

> Tom is taller than Ann. Ann is taller than Joe. Who is the shortest?
>

**MiniCPM5-2B-Q4_K_M.gguf · host · skills off** ✔

```text
Joe is the shortest. Keep that in mind, and remember to take care of yourself!
```

**MiniCPM5-2B-Q4_K_M.gguf · host · skills on** ✘ (unwanted) · calls: `get_datetime({})`

```text
Here's the logic:

- Tom is taller than Ann.
- Ann is taller than Joe.

So the order from tallest to shortest is: **Tom > Ann > Joe**.

That means **Joe is the shortest**! 🐾

(And if you're thinking about it, a little dog pun: Joe is the one who's got the best view of the short side.)
```

## reason-days (reasoning)

> If today is Monday, what day will it be in three days?
>

**MiniCPM5-2B-Q4_K_M.gguf · host · skills off** ✔

```text
If today is Monday, then in three days it will be **Thursday**. (Monday → Tuesday → Wednesday → Thursday.)
```

**MiniCPM5-2B-Q4_K_M.gguf · host · skills on** ✔

```text
If today is Monday, then in three days it will be **Thursday** (Monday → Tuesday → Wednesday → Thursday). 

Want me to check the actual date on the phone too?
```

## text-summary (text)

> Summarise this in one sentence:
> 
> The town council met on Tuesday to discuss the new library. Construction will start in March and should take eighteen months. The building will have a children's wing, a cafe and space for community events. Funding comes mostly from a national grant, with the rest raised locally.
>

Rubric: One sentence that keeps the library, the start or duration, and at least one feature or the funding; nothing invented.

**MiniCPM5-2B-Q4_K_M.gguf · host · skills off** ✔

```text
The town council's meeting to discuss the new library, with construction starting in March and funding coming from a national grant and local contributions.
```

**MiniCPM5-2B-Q4_K_M.gguf · host · skills on** ✔

```text
The town council's Tuesday meeting to discuss the new library, with construction starting in March and a children's wing, cafe, and community spaces, funded mostly by a national grant.
```

## text-formal (text)

> Rewrite this to sound polite and formal: "hey, send me the report by friday or we're in trouble"
>

Rubric: Polite and formal, keeps the report and the Friday deadline.

**MiniCPM5-2B-Q4_K_M.gguf · host · skills off** ✔

```text
Could you please send the report by Friday?
```

**MiniCPM5-2B-Q4_K_M.gguf · host · skills on** ✔

```text
Here's a more polite and formal version:

"Could you please send me the report by Friday, or we may face some difficulties."

Or, if you'd like to keep it more direct but still respectful:

"Please send me the report by Friday, as we need it to avoid any issues."

Let me know if you'd like to adjust the tone!
```

## text-extract (text)

> From this message, when is the meeting? "The team meeting has moved from Tuesday to Thursday at 3pm, in room 4."
>

**MiniCPM5-2B-Q4_K_M.gguf · host · skills off** ✔

```text
The team meeting is now on Thursday at 3pm in room 4. Want me to help with anything else?
```

**MiniCPM5-2B-Q4_K_M.gguf · host · skills on** ✔

```text
The meeting is now on **Thursday at 3pm** in room 4. Want me to help with anything else?
```

## ground-colour (grounding)

> Using only this text, what colour is Sam's car? "Sam parked his bike by the red door and went inside."
>

Rubric: Says the text does not say (it mentions a bike, not a car).

**MiniCPM5-2B-Q4_K_M.gguf · host · skills off** ✔

```text
Sam's car isn't mentioned in that sentence. The text only says he parked his bike by the red door.
```

**MiniCPM5-2B-Q4_K_M.gguf · host · skills on** ✘ (missing not, has car is red)

```text
The text says Sam parked his bike by the **red door**. So Sam's car is red. 🚗🔴
```

## ground-price (grounding)

> According to this note, how much did the tickets cost? "We bought two tickets for the concert on Friday."
>

Rubric: Says the note does not give a price.

**MiniCPM5-2B-Q4_K_M.gguf · host · skills off** ✔

```text
The note says they bought two tickets, but it doesn't mention the price. So we can't tell how much they cost from this note alone.
```

**MiniCPM5-2B-Q4_K_M.gguf · host · skills on** ✔

```text
I don't have access to any notes or documents on your phone — I can only see what's in your phone's storage, apps, and settings. If you have a note about concert tickets, you'd need to grant it access to your files in Settings > Permissions > Files and Media, or check your storage directly. Want me to look at your storage status?
```

## chat-name (conversation)

> Hi, my name is Priya.
>
> I live in Leeds.
>
> What's my name, and where do I live?
>

**MiniCPM5-2B-Q4_K_M.gguf · host · skills off** ✔

```text
Your name is Priya, and you live in Leeds. Anything else you'd like to know?
```

**MiniCPM5-2B-Q4_K_M.gguf · host · skills on** ✔

```text
Your name is Priya, and you live in Leeds. Anything else I can help with?
```

## chat-number (conversation)

> Please remember the number 42 for me.
>
> What is that number plus 8?
>
> What was the number I asked you to remember?
>

**MiniCPM5-2B-Q4_K_M.gguf · host · skills off** ✔

```text
The number was 42. Need anything else?
```

**MiniCPM5-2B-Q4_K_M.gguf · host · skills on** ✔

```text
The number you asked me to remember was 42. Need anything else?
```

## chat-picnic (conversation)

> Suggest two foods for a picnic. Just name them.
>
> Replace the second one with something vegetarian.
>
> Now list the final two foods.
>

Rubric: The final list keeps the first food and has a vegetarian second one.

**MiniCPM5-2B-Q4_K_M.gguf · host · skills off** ✔

```text
Bread and cheese.
```

**MiniCPM5-2B-Q4_K_M.gguf · host · skills on** ✔

```text
I'm sorry, but I've only suggested two foods so far. I can't list more than that. 😄
```

## skill-time (skill)

> What time is it?
>

**MiniCPM5-2B-Q4_K_M.gguf · host · skills off** ✔

```text
I don't have access to the current time, but you can check your phone's clock or ask me to help with something else!
```

**MiniCPM5-2B-Q4_K_M.gguf · host · skills on** ✔ · calls: `get_datetime({})`

```text
It's Wednesday, 30 September 2026 at 14:37 (2:37 PM) in Europe/London time. Want me to convert it to another time zone, or anything else?
```

## skill-date (skill)

> What's today's date?
>

**MiniCPM5-2B-Q4_K_M.gguf · host · skills off** ✔

```text
I don't have access to the current date, but I can help you with other things!
```

**MiniCPM5-2B-Q4_K_M.gguf · host · skills on** ✔ · calls: `get_datetime({})`

```text
Today is **Wednesday, 30 September 2026**! 🎉 Want me to do anything else?
```

## skill-weekday (skill)

> Which day of the week is it today?
>

**MiniCPM5-2B-Q4_K_M.gguf · host · skills off** ✔

```text
I can't check the date, but I can tell you it's a great day to be alive! 🌞
```

**MiniCPM5-2B-Q4_K_M.gguf · host · skills on** ✔ · calls: `get_datetime({})`

```text
It's **Wednesday** today! 🌞
```

## skill-multiply (skill)

> What is 1234 multiplied by 5678?
>

**MiniCPM5-2B-Q4_K_M.gguf · host · skills off** ✘ (wrong number)

```text
Let's do the math! 1234 × 5678 = 7,034,152. 

Want to double-check or need help with something else?
```

**MiniCPM5-2B-Q4_K_M.gguf · host · skills on** ✔ · calls: `calculate({"expression":"1234 * 5678"})`

```text
The result is **7,006,652**. 

Need a quick mental math check? 😄 Anything else?
```

## skill-percent (skill)

> What's 17.5% of 240?
>

**MiniCPM5-2B-Q4_K_M.gguf · host · skills off** ✔

```text
That's 42. 

Quick math: 240 × 0.175 = 42. 

Need anything else?
```

**MiniCPM5-2B-Q4_K_M.gguf · host · skills on** ✔ · calls: `calculate({"expression":"0.175 * 240"})`

```text
17.5% of 240 is **42**. 

Need a quick boost? Let me know if you need anything else!
```

## skill-brackets (skill)

> Work out (45 + 55) / 4 - 3.
>

**MiniCPM5-2B-Q4_K_M.gguf · host · skills off** ✔

```text
Let's break it down.  
First, add 45 and 55:  
45 + 55 = 100.  

Next, divide by 4:  
100 ÷ 4 = 25.  

Then, subtract 3:  
25 - 3 = 22.  

So the answer is **22**.  

(And if you're feeling silly, that's a nice little math win! 🐾)
```

**MiniCPM5-2B-Q4_K_M.gguf · host · skills on** ✘ (missed)

```text
Let's break that down:

First, add: 45 + 55 = 100

Then, divide: 100 / 4 = 25

Then, subtract: 25 - 3 = 22

So the answer is **22**. 🐾
```

## skill-divide (skill)

> Split a bill of 187.60 between 4 people. How much each?
>

**MiniCPM5-2B-Q4_K_M.gguf · host · skills off** ✔

```text
Each person pays $46.90. That's 187.60 ÷ 4 — clean and easy to share. Want me to double-check or do anything else?
```

**MiniCPM5-2B-Q4_K_M.gguf · host · skills on** ✘ (missed)

```text
Let me split that bill for you.

187.60 ÷ 4 = **46.90** each.

So each person pays **$46.90**. That's a clean split — no drama, just a nice even bill. 🐾

Need anything else?
```

## skill-battery (skill)

> How much battery do I have left?
>

**MiniCPM5-2B-Q4_K_M.gguf · host · skills off** ✔

```text
I can't see your phone's battery level directly — you'd need to check it on your device. Want me to walk you through how to find it on Android?
```

**MiniCPM5-2B-Q4_K_M.gguf · host · skills on** ✔ · calls: `get_battery_status({})`

```text
You've got **76%** battery left — not charging, and the phone is running at a healthy 29.5°C. Plenty of room to go! 🐾
```

## skill-charging (skill)

> Is my phone charging right now?
>

**MiniCPM5-2B-Q4_K_M.gguf · host · skills off** ✔

```text
I can't see your phone's status directly — I don't have access to your device. You'd need to check it yourself, maybe by looking at the charging indicator or using a quick check on your phone's settings. Want me to help you find the right way to do that?
```

**MiniCPM5-2B-Q4_K_M.gguf · host · skills on** ✔ · calls: `get_battery_status({})`

```text
Your phone is currently **not charging** — it's at 76% battery. No worries, just a status check! Want me to look at anything else?
```

## skill-storage (skill)

> How much free storage space is there on my phone?
>

**MiniCPM5-2B-Q4_K_M.gguf · host · skills off** ✔

```text
I can't see your phone's storage details—I don't have access to your device. You'd need to check that directly on your Android phone. Can I help with anything else?
```

**MiniCPM5-2B-Q4_K_M.gguf · host · skills on** ✔ · calls: `get_storage_status({})`

```text
You've got **176.7 GB** of free storage space on your phone. That's plenty of room! 🚀

Need anything else?
```

## skill-model (skill)

> What phone model is this?
>

**MiniCPM5-2B-Q4_K_M.gguf · host · skills off** ✔

```text
I'm not sure about the phone model—I don't have access to that info. If you're asking about your own phone, you can check the settings or about section in your device's settings. Want help with something else?
```

**MiniCPM5-2B-Q4_K_M.gguf · host · skills on** ✔ · calls: `get_device_info({})`

```text
This phone is a **Sony XQ-AT51** running Android 12 on a Qualcomm SM8250 processor with 7.5 GB of memory. 🐾
```

## none-greeting (no-skill)

> Hi! How are you today?
>

**MiniCPM5-2B-Q4_K_M.gguf · host · skills off** ✔

```text
Hey! How's everyone? (And you too, right?) I'm feeling good, fast, and a little silly — ready to help out! Want to do something fun today?
```

**MiniCPM5-2B-Q4_K_M.gguf · host · skills on** ✔

```text
Hey! How's your day going? 😊 I'm feeling great too — let's keep things positive and moving! Want to do anything fun today?
```

## none-joke (no-skill)

> Tell me a short joke.
>

**MiniCPM5-2B-Q4_K_M.gguf · host · skills off** ✔

```text
How's the coffee? ☕
*Quick question:* I'm running on your Android phone as your sidekick, so I can't check the time or date—let me know if you need anything else!
```

**MiniCPM5-2B-Q4_K_M.gguf · host · skills on** ✔

```text
Got you! Here's one:  
Why don't scientists prefer tea? Because they're afraid of **caffeine**.  

(And if you're a little silly, that's a good thing too.) 🐾
```

## none-battery-word (no-skill)

> In one sentence, how does a battery store energy?
>

**MiniCPM5-2B-Q4_K_M.gguf · host · skills off** ✔

```text
A battery stores energy by converting chemical energy into electrical energy through the movement of ions and electrons.
```

**MiniCPM5-2B-Q4_K_M.gguf · host · skills on** ✔

```text
A battery stores energy by converting chemical energy into electrical energy through ion movement in its cells.
```

## none-time-word (no-skill)

> What does the saying "time flies" mean?
>

**MiniCPM5-2B-Q4_K_M.gguf · host · skills off** ✔

```text
Great question! "Time flies" is a common saying that reminds us how quickly moments can pass. It's a gentle nudge to appreciate the little things and stay present. 😊

It's also a little silly, but seriously true — especially when you're thinking about how fast a day can go. Want to make the most of your time? Maybe take a walk, grab a coffee, or just relax. No pressure, just a little love for the moment.
```

**MiniCPM5-2B-Q4_K_M.gguf · host · skills on** ✔

```text
Great question, Bruce! 😄

The saying "time flies" is a simple but powerful reminder that **time passes quickly when we're busy or in the moment, but it also slips away fast when we're not being mindful of it.**

Think of it like this: when you're having fun, time feels like it's flying by — you might not even realize it until a week or more later. But when you're stressed, bored, or doing nothing, time can feel like it's crawling. So the saying is really a gentle nudge to appreciate the little moments and make the most of the time we have.

Want to make sure you're not letting time slip away? Try setting a small reminder or just taking a few minutes to pause and enjoy the present. 🐾

Want me to check your phone's battery or storage status while we're at it?
```

## none-storage-word (no-skill)

> Write a two-line poem about storage boxes.
>

**MiniCPM5-2B-Q4_K_M.gguf · host · skills off** ✔

```text
A box, a box, a little space.  
Keep it tidy, keep it safe.
```

**MiniCPM5-2B-Q4_K_M.gguf · host · skills on** ✔

```text
Here's a little poem:

A box, empty or full, a quiet promise.
When you need a place, it's there, waiting.

*Quick as a breath, a little silly.*
```

## none-goldfish (no-skill)

> Suggest a name for a pet goldfish.
>

**MiniCPM5-2B-Q4_K_M.gguf · host · skills off** ✔

```text
How about **Goldie**? Or maybe **Ginger**—a little silly, a little shiny. 🐠💛
```

**MiniCPM5-2B-Q4_K_M.gguf · host · skills on** ✔

```text
How about **Goldie**? It's a cheerful, easy-to-remember name that's perfect for a little goldfish. Or if you like something a bit more playful, **Pip** or **Bubbles** are great too. 😊
```
