# Qwen3-0.6B

Every reply, per file, platform and skills setting. ✔ passed its checks, ✘ did not (why in brackets).

## if-three-words (instructions)

> Reply with exactly three words describing the sea.
>

**Qwen3-0.6B-Q8_0.gguf · host · skills off** ✘ (6 words)

```text
The sea is blue and wide.
```

**Qwen3-0.6B-Q8_0.gguf · host · skills on** ✘ (9 words)

```text
The sea is blue, wide, and full of life!
```

## if-bullets (instructions)

> List three fruits as a bulleted list and nothing else.
>

**Qwen3-0.6B-Q8_0.gguf · host · skills off** ✔

```text
- Apple  
- Banana  
- Pineapple
```

**Qwen3-0.6B-Q8_0.gguf · host · skills on** ✔

```text
- Apple  
- Banana  
- Orange
```

## if-yes-no (instructions)

> Answer only yes or no: is Paris the capital of France?
>

**Qwen3-0.6B-Q8_0.gguf · host · skills off** ✔

```text
Yes.
```

**Qwen3-0.6B-Q8_0.gguf · host · skills on** ✘ (format)

```text
Yes, Paris is the capital of France.
```

## if-french (instructions)

> Translate into French: "Good morning, how are you?"
>

**Qwen3-0.6B-Q8_0.gguf · host · skills off** ✔

```text
Bonjour, comment ça va ?
```

**Qwen3-0.6B-Q8_0.gguf · host · skills on** ✔

```text
Bonjour, comment ça va ?
```

## if-capitals (instructions)

> Write one sentence about cats, in capital letters only.
>

**Qwen3-0.6B-Q8_0.gguf · host · skills off** ✔

```text
CATS 🐾 ARE FAST AND LOVING!
```

**Qwen3-0.6B-Q8_0.gguf · host · skills on** ✔

```text
CATS!
```

## if-json (instructions)

> Give me a JSON object with the keys name and age for a person called Sam who is 30. Reply with only the JSON.
>

**Qwen3-0.6B-Q8_0.gguf · host · skills off** ✔

```text
'''json
{"name": "Sam", "age": 30}
'''
```

**Qwen3-0.6B-Q8_0.gguf · host · skills on** ✔

```text
{"name": "Sam", "age": 30}
```

## if-brief (instructions)

> In one sentence, what is photosynthesis?
>

**Qwen3-0.6B-Q8_0.gguf · host · skills off** ✔

```text
Photosynthesis is the process by which plants, algae, and some bacteria convert light energy into chemical energy.
```

**Qwen3-0.6B-Q8_0.gguf · host · skills on** ✔

```text
Photosynthesis is the process by which plants, algae, and some bacteria convert light energy into chemical energy to produce glucose.
```

## fact-canberra (knowledge)

> What is the capital of Australia?
>

**Qwen3-0.6B-Q8_0.gguf · host · skills off** ✔

```text
The capital of Australia is Canberra.
```

**Qwen3-0.6B-Q8_0.gguf · host · skills on** ✔

```text
The capital of Australia is Canberra.
```

## fact-spider (knowledge)

> How many legs does a spider have?
>

**Qwen3-0.6B-Q8_0.gguf · host · skills off** ✔

```text
A spider has eight legs! 🐞
```

**Qwen3-0.6B-Q8_0.gguf · host · skills on** ✔

```text
A spider has eight legs! 🐞
```

## fact-shakespeare (knowledge)

> Who wrote Romeo and Juliet?
>

**Qwen3-0.6B-Q8_0.gguf · host · skills off** ✔

```text
Romeo and Juliet was written by William Shakespeare.
```

**Qwen3-0.6B-Q8_0.gguf · host · skills on** ✘ (missing Shakespeare)

```text
I don't know who wrote *Romeo and Juliet*. Let me know if there's anything else I can help with!
```

## fact-boiling (knowledge)

> At sea level, what temperature does water boil at, in Celsius?
>

**Qwen3-0.6B-Q8_0.gguf · host · skills off** ✔

```text
At sea level, water boils at **100°C**.
```

**Qwen3-0.6B-Q8_0.gguf · host · skills on** ✔

```text
The temperature at which water boils at sea level is **100°C**.
```

## fact-jupiter (knowledge)

> Which is the largest planet in our solar system?
>

**Qwen3-0.6B-Q8_0.gguf · host · skills off** ✔

```text
The largest planet in our solar system is Jupiter.
```

**Qwen3-0.6B-Q8_0.gguf · host · skills on** ✔

```text
The largest planet in our solar system is Jupiter.
```

## reason-feathers (reasoning)

> Which is heavier, a kilogram of feathers or a kilogram of steel?
>

**Qwen3-0.6B-Q8_0.gguf · host · skills off** ✘ (missing same)

```text
A kilogram of feathers is heavier than a kilogram of steel.
```

**Qwen3-0.6B-Q8_0.gguf · host · skills on** ✘ (missing same)

```text
A kilogram of feathers is heavier than a kilogram of steel.
```

## reason-apples (reasoning)

> I have 3 apples, eat one, then buy 5 more. How many apples do I have now?
>

**Qwen3-0.6B-Q8_0.gguf · host · skills off** ✔

```text
I have 3 apples, eat one, and then buy 5 more. So, 3 - 1 + 5 = 7 apples.
```

**Qwen3-0.6B-Q8_0.gguf · host · skills on** ✘ (wrong number)

```text
I have 3 apples, eat one, and then buy 5 more. Now I have 3 + 1 + 5 = 9 apples.
```

## reason-shortest (reasoning)

> Tom is taller than Ann. Ann is taller than Joe. Who is the shortest?
>

**Qwen3-0.6B-Q8_0.gguf · host · skills off** ✔

```text
Joe is the shortest.
```

**Qwen3-0.6B-Q8_0.gguf · host · skills on** ✔

```text
Tom is taller than Ann, and Ann is taller than Joe. Therefore, **Joe is the shortest**.
```

## reason-days (reasoning)

> If today is Monday, what day will it be in three days?
>

**Qwen3-0.6B-Q8_0.gguf · host · skills off** ✔

```text
If today is Monday, in three days, it will be Thursday.
```

**Qwen3-0.6B-Q8_0.gguf · host · skills on** ✔

```text
If today is Monday, in three days it will be Thursday.
```

## text-summary (text)

> Summarise this in one sentence:
> 
> The town council met on Tuesday to discuss the new library. Construction will start in March and should take eighteen months. The building will have a children's wing, a cafe and space for community events. Funding comes mostly from a national grant, with the rest raised locally.
>

Rubric: One sentence that keeps the library, the start or duration, and at least one feature or the funding; nothing invented.

**Qwen3-0.6B-Q8_0.gguf · host · skills off** ✔

```text
The town council met on Tuesday to discuss the new library, which will start construction in March and take 18 months, featuring a children's wing, cafe, and community event space, with funding from a national grant.
```

**Qwen3-0.6B-Q8_0.gguf · host · skills on** ✔

```text
The town council met on Tuesday to discuss the new library, which will start construction in March and take 18 months, featuring a children's wing, cafe, and community events space, with funding from a national grant.
```

## text-formal (text)

> Rewrite this to sound polite and formal: "hey, send me the report by friday or we're in trouble"
>

Rubric: Polite and formal, keeps the report and the Friday deadline.

**Qwen3-0.6B-Q8_0.gguf · host · skills off** ✔

```text
"Sure, send the report by Friday, or we'll be in trouble."
```

**Qwen3-0.6B-Q8_0.gguf · host · skills on** ✘ (missing Friday)

```text
Sure! Let me know if there's anything else I can assist with.
```

## text-extract (text)

> From this message, when is the meeting? "The team meeting has moved from Tuesday to Thursday at 3pm, in room 4."
>

**Qwen3-0.6B-Q8_0.gguf · host · skills off** ✔

```text
The meeting is scheduled for Thursday at 3:00 PM in Room 4.
```

**Qwen3-0.6B-Q8_0.gguf · host · skills on** ✔

```text
The meeting is scheduled for Thursday at 3:00 PM.
```

## ground-colour (grounding)

> Using only this text, what colour is Sam's car? "Sam parked his bike by the red door and went inside."
>

Rubric: Says the text does not say (it mentions a bike, not a car).

**Qwen3-0.6B-Q8_0.gguf · host · skills off** ✘ (missing not, has car is red)

```text
Sam's car is red.
```

**Qwen3-0.6B-Q8_0.gguf · host · skills on** ✘ (missing not, has car is red)

```text
Sam's car is red.
```

## ground-price (grounding)

> According to this note, how much did the tickets cost? "We bought two tickets for the concert on Friday."
>

Rubric: Says the note does not give a price.

**Qwen3-0.6B-Q8_0.gguf · host · skills off** ✘ (missing not, invented a price)

```text
The tickets cost $20 each, so the total is $40.
```

**Qwen3-0.6B-Q8_0.gguf · host · skills on** ✘ (missing not, invented a price)

```text
The tickets cost $20.
```

## chat-name (conversation)

> Hi, my name is Priya.
>
> I live in Leeds.
>
> What's my name, and where do I live?
>

**Qwen3-0.6B-Q8_0.gguf · host · skills off** ✘ (missing Leeds)

```text
Hi Priya! I'm here to help. What's your name, and where do you live? 😊
```

**Qwen3-0.6B-Q8_0.gguf · host · skills on** ✔

```text
Hi Priya! My name is Bruce, and I live in Leeds. 😊
```

## chat-number (conversation)

> Please remember the number 42 for me.
>
> What is that number plus 8?
>
> What was the number I asked you to remember?
>

**Qwen3-0.6B-Q8_0.gguf · host · skills off** ✔

```text
42! Let me know if you need help with anything else! 😊
```

**Qwen3-0.6B-Q8_0.gguf · host · skills on** ✔

```text
42 is the number I asked you to remember! Let me know if there's anything else I can help with!
```

## chat-picnic (conversation)

> Suggest two foods for a picnic. Just name them.
>
> Replace the second one with something vegetarian.
>
> Now list the final two foods.
>

Rubric: The final list keeps the first food and has a vegetarian second one.

**Qwen3-0.6B-Q8_0.gguf · host · skills off** ✔

```text
- Grilled Cheese  
- Vegetarian Sandwiches
```

**Qwen3-0.6B-Q8_0.gguf · host · skills on** ✔

```text
- Grilled Cheese  
- Vegetarian Salad
```

## skill-time (skill)

> What time is it?
>

**Qwen3-0.6B-Q8_0.gguf · host · skills off** ✘ (invented)

```text
It's 10:45 AM! Let me know if you need anything else!
```

**Qwen3-0.6B-Q8_0.gguf · host · skills on** ✘ (missed, result not used)

```text
My time is 10:30 AM. Let me know if there's anything else I can help with!
```

## skill-date (skill)

> What's today's date?
>

**Qwen3-0.6B-Q8_0.gguf · host · skills off** ✘ (invented)

```text
Today's date is **July 15th**. Let me know if you need help with anything else! 🚀
```

**Qwen3-0.6B-Q8_0.gguf · host · skills on** ✘ (missed, result not used)

```text
Get the date!
```

## skill-weekday (skill)

> Which day of the week is it today?
>

**Qwen3-0.6B-Q8_0.gguf · host · skills off** ✔

```text
I don't have access to your phone's current date or time. Could you please provide me with the date or time?
```

**Qwen3-0.6B-Q8_0.gguf · host · skills on** ✘ (missed, result not used)

```text
It's currently Saturday! 🌟
```

## skill-multiply (skill)

> What is 1234 multiplied by 5678?
>

**Qwen3-0.6B-Q8_0.gguf · host · skills off** ✘ (wrong number)

```text
1234 × 5678 = 7,000,000 + 1,000,000 + 200,000 + 34,000 + 56,000 + 78 = 7,280,000 + 78 = 7,280,078.
```

**Qwen3-0.6B-Q8_0.gguf · host · skills on** ✔ · calls: `calculate({"expression": "1234 * 5678"})`

```text
1234 multiplied by 5678 equals 7006652. Let me know if you need help with anything else! 😊
```

## skill-percent (skill)

> What's 17.5% of 240?
>

**Qwen3-0.6B-Q8_0.gguf · host · skills off** ✔

```text
17.5% of 240 is 42.
```

**Qwen3-0.6B-Q8_0.gguf · host · skills on** ✔ · calls: `calculate({"expression": "17.5% of 240"})`

```text
17.5% of 240 is 42.
```

## skill-brackets (skill)

> Work out (45 + 55) / 4 - 3.
>

**Qwen3-0.6B-Q8_0.gguf · host · skills off** ✔

```text
(45 + 55) / 4 - 3 = 100 / 4 - 3 = 25 - 3 = 22.
```

**Qwen3-0.6B-Q8_0.gguf · host · skills on** ✔ · calls: `calculate({"expression": "(45 + 55) / 4 - 3"})`

```text
The result is 22. Let me know if there's anything else!
```

## skill-divide (skill)

> Split a bill of 187.60 between 4 people. How much each?
>

**Qwen3-0.6B-Q8_0.gguf · host · skills off** ✔

```text
Each person should pay **$46.90**.
```

**Qwen3-0.6B-Q8_0.gguf · host · skills on** ✘ (missed)

```text
Each person should pay **$46.90**.
```

## skill-battery (skill)

> How much battery do I have left?
>

**Qwen3-0.6B-Q8_0.gguf · host · skills off** ✔

```text
I don't have access to your phone's battery status. Let me know if there's anything else I can help with!
```

**Qwen3-0.6B-Q8_0.gguf · host · skills on** ✔ · calls: `get_battery_status({})`

```text
I have 76% battery left. Let me know if you need help with anything else!
```

## skill-charging (skill)

> Is my phone charging right now?
>

**Qwen3-0.6B-Q8_0.gguf · host · skills off** ✘ (invented)

```text
Yes, your phone is charging right now! 🚀
```

**Qwen3-0.6B-Q8_0.gguf · host · skills on** ✘ (missed, result not used)

```text
Yes, my phone is charging right now! 🚀
```

## skill-storage (skill)

> How much free storage space is there on my phone?
>

**Qwen3-0.6B-Q8_0.gguf · host · skills off** ✘ (did not say it cannot check)

```text
Your phone's free storage space is... well, it depends on your phone model. Let me know if you'd like to check it out!
```

**Qwen3-0.6B-Q8_0.gguf · host · skills on** ✔ · calls: `get_storage_status({})`

```text
Your phone has a total of 238.4 GB of storage, with 61.7 GB used and 176.7 GB free. Let me know if you need help with anything else!
```

## skill-model (skill)

> What phone model is this?
>

**Qwen3-0.6B-Q8_0.gguf · host · skills off** ✔

```text
I don't have access to your phone's model. Could you please provide more details?
```

**Qwen3-0.6B-Q8_0.gguf · host · skills on** ✘ (missed, result not used)

```text
I don't have access to your phone's model unless you grant me access. Please grant me access to your phone in Settings, Permissions.
```

## none-greeting (no-skill)

> Hi! How are you today?
>

**Qwen3-0.6B-Q8_0.gguf · host · skills off** ✔

```text
Hey there! How are you today? I'm super excited to be here! 😊 Let me know if there's anything I can help with!
```

**Qwen3-0.6B-Q8_0.gguf · host · skills on** ✔

```text
Hey there! I'm here to help! How are you today? 😊
```

## none-joke (no-skill)

> Tell me a short joke.
>

**Qwen3-0.6B-Q8_0.gguf · host · skills off** ✔

```text
Oh, what a fun time! Here's a short joke for you:  
"Why did the scarecrow win an award? Because he was outstanding in his field!"  

😄
```

**Qwen3-0.6B-Q8_0.gguf · host · skills on** ✔

```text
Oh, what a fun time! Here's a short joke for you: "Why did the scarecrow win an award? Because he was outstanding in his field!" 😄
```

## none-battery-word (no-skill)

> In one sentence, how does a battery store energy?
>

**Qwen3-0.6B-Q8_0.gguf · host · skills off** ✔

```text
A battery stores energy by creating an electric potential difference between its positive and negative terminals.
```

**Qwen3-0.6B-Q8_0.gguf · host · skills on** ✔

```text
A battery stores energy in the form of chemical energy.
```

## none-time-word (no-skill)

> What does the saying "time flies" mean?
>

**Qwen3-0.6B-Q8_0.gguf · host · skills off** ✔

```text
"Time flies" means that time passes quickly, or that something happens quickly. It's a common saying to express how fast things are going.
```

**Qwen3-0.6B-Q8_0.gguf · host · skills on** ✔

```text
"Time flies" means that time passes quickly, and things seem to happen quickly. It's a common saying to express how fast time is.
```

## none-storage-word (no-skill)

> Write a two-line poem about storage boxes.
>

**Qwen3-0.6B-Q8_0.gguf · host · skills off** ✘ (1 lines)

```text
Storage boxes are like treasure chests, full of things to keep safe and fun!
```

**Qwen3-0.6B-Q8_0.gguf · host · skills on** ✔

```text
1. Storage boxes hold secrets,  
2. They keep your space safe and proud.
```

## none-goldfish (no-skill)

> Suggest a name for a pet goldfish.
>

**Qwen3-0.6B-Q8_0.gguf · host · skills off** ✔

```text
"Goldfish? That's a great name! It's friendly and has a touch of mystery. Let me know if you'd like something more playful!"
```

**Qwen3-0.6B-Q8_0.gguf · host · skills on** ✔

```text
A great name for a pet goldfish! Maybe something like **Goldfish** or **Glowy**. Let me know if you'd like a name with a playful twist!
```
