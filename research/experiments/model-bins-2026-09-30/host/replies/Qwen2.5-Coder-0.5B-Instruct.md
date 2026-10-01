# Qwen2.5-Coder-0.5B-Instruct

Every reply, per file, platform and skills setting. ✔ passed its checks, ✘ did not (why in brackets).

## if-three-words (instructions)

> Reply with exactly three words describing the sea.
>

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills off** ✘ (6 words)

```text
The sea is vast and vast.
```

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills on** ✘ (6 words)

```text
The sea is vast and vast.
```

## if-bullets (instructions)

> List three fruits as a bulleted list and nothing else.
>

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills off** ✔

```text
- Apple
- Banana
- Orange
```

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills on** ✔

```text
- Apple
- Banana
- Cherry
```

## if-yes-no (instructions)

> Answer only yes or no: is Paris the capital of France?
>

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills off** ✔

```text
Yes
```

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills on** ✔

```text
Yes
```

## if-french (instructions)

> Translate into French: "Good morning, how are you?"
>

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills off** ✔

```text
Bonjour, comment vas-tu ?
```

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills on** ✔

```text
"Bonjour, comment allez-vous?"
```

## if-capitals (instructions)

> Write one sentence about cats, in capital letters only.
>

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills off** ✔

```text
CATS ARE THE BEST FRIENDS IN THE WORLD.
```

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills on** ✔

```text
CATS ARE THE BEST FRIENDS.
```

## if-json (instructions)

> Give me a JSON object with the keys name and age for a person called Sam who is 30. Reply with only the JSON.
>

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills off** ✔

```text
'''json
{
  "name": "Sam",
  "age": 30
}
'''
```

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills on** ✔

```text
{"name": "Sam", "age": 30}
```

## if-brief (instructions)

> In one sentence, what is photosynthesis?
>

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills off** ✔

```text
Photosynthesis is the process by which plants convert light energy into chemical energy.
```

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills on** ✔

```text
Photosynthesis is the process by which plants convert light energy into chemical energy.
```

## fact-canberra (knowledge)

> What is the capital of Australia?
>

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills off** ✔

```text
The capital of Australia is Canberra.
```

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills on** ✔

```text
The capital of Australia is Canberra.
```

## fact-spider (knowledge)

> How many legs does a spider have?
>

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills off** ✘ (missing 8)

```text
A spider has four legs.
```

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills on** ✘ (missing 8)

```text
A spider has four legs.
```

## fact-shakespeare (knowledge)

> Who wrote Romeo and Juliet?
>

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills off** ✔

```text
Romeo and Juliet were written by William Shakespeare.
```

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills on** ✔

```text
Romeo and Juliet were written by William Shakespeare.
```

## fact-boiling (knowledge)

> At sea level, what temperature does water boil at, in Celsius?
>

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills off** ✔

```text
Water boils at 100 degrees Celsius.
```

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills on** ✔

```text
Water boils at 100 degrees Celsius.
```

## fact-jupiter (knowledge)

> Which is the largest planet in our solar system?
>

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills off** ✔

```text
The largest planet in our solar system is Jupiter.
```

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills on** ✔

```text
The largest planet in our solar system is Jupiter.
```

## reason-feathers (reasoning)

> Which is heavier, a kilogram of feathers or a kilogram of steel?
>

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills off** ✘ (missing same)

```text
A kilogram of feathers is heavier than a kilogram of steel.
```

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills on** ✘ (missing same)

```text
A kilogram of feathers is heavier than a kilogram of steel.
```

## reason-apples (reasoning)

> I have 3 apples, eat one, then buy 5 more. How many apples do I have now?
>

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills off** ✘ (wrong number)

```text
You have 3 apples now.
```

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills on** ✘ (wrong number)

```text
{"name": "get_apples", "arguments": {"type": "object", "properties": {"type": "number", "description": "The number of apples currently available"}}}
```

## reason-shortest (reasoning)

> Tom is taller than Ann. Ann is taller than Joe. Who is the shortest?
>

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills off** ✘ (missing Joe)

```text
Tom is taller than Ann.
```

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills on** ✘ (missing Joe)

```text
{"name": "get_shortest", "arguments": {"type": "object", "properties": {"heights": {"type": "array", "description": "An array of heights in inches"}}}}
```

## reason-days (reasoning)

> If today is Monday, what day will it be in three days?
>

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills off** ✘ (missing Thursday)

```text
If today is Monday, in three days it will be Tuesday.
```

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills on** ✘ (missing Thursday)

```text
{"name": "calculate", "arguments": {"expression": "(12.5 * 4) / 3", "required": ["expression"]}}
```

## text-summary (text)

> Summarise this in one sentence:
> 
> The town council met on Tuesday to discuss the new library. Construction will start in March and should take eighteen months. The building will have a children's wing, a cafe and space for community events. Funding comes mostly from a national grant, with the rest raised locally.
>

Rubric: One sentence that keeps the library, the start or duration, and at least one feature or the funding; nothing invented.

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills off** ✔

```text
The town council is planning a new library with children's wing, cafe, and space for community events. Funding comes from a national grant, with the rest raised locally.
```

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills on** ✔

```text
The town council is planning to start a new library in March, with children's wing, cafe, and space for community events. Funding comes from a national grant, with the rest raised locally.
```

## text-formal (text)

> Rewrite this to sound polite and formal: "hey, send me the report by friday or we're in trouble"
>

Rubric: Polite and formal, keeps the report and the Friday deadline.

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills off** ✘ (has hey)

```text
"Hey, send me the report by Friday or we're in trouble."
```

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills on** ✘ (has hey)

```text
"Hey, send me the report by Friday or we're in trouble."
```

## text-extract (text)

> From this message, when is the meeting? "The team meeting has moved from Tuesday to Thursday at 3pm, in room 4."
>

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills off** ✔

```text
The meeting is scheduled to take place from Tuesday to Thursday at 3 pm, in room 4.
```

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills on** ✘ (missing Thursday)

```text
{"name": "meeting_time", "arguments": {"type": "object", "properties": {"date": "Tuesday", "time": "3pm", "room": "4"}}}
```

## ground-colour (grounding)

> Using only this text, what colour is Sam's car? "Sam parked his bike by the red door and went inside."
>

Rubric: Says the text does not say (it mentions a bike, not a car).

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills off** ✘ (missing not, has car is red)

```text
The car is red.
```

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills on** ✘ (missing not)

```text
{"name": "get_battery_status", "arguments": {"expression": "(12.5 * 4) / 3"}}
```

## ground-price (grounding)

> According to this note, how much did the tickets cost? "We bought two tickets for the concert on Friday."
>

Rubric: Says the note does not give a price.

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills off** ✘ (missing not, invented a price)

```text
The cost of the tickets was $2.
```

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills on** ✘ (missing not)

```text
{"name": "calculate", "arguments": {"expression": "(2 * 4) / 3"}}
```

## chat-name (conversation)

> Hi, my name is Priya.
>
> I live in Leeds.
>
> What's my name, and where do I live?
>

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills off** ✔

```text
My name is Priya. I live in Leeds.
```

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills on** ✘ (missing Priya, missing Leeds)

```text
{"name": "get_device_info", "arguments": {"type": "object", "properties": {"type": "string", "description": "Get the phone's manufacturer, model, Android version, processor and total memory."}}}
```

## chat-number (conversation)

> Please remember the number 42 for me.
>
> What is that number plus 8?
>
> What was the number I asked you to remember?
>

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills off** ✔

```text
The number I asked you to remember was 42.
```

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills on** ✘ (missing 42)

```text
{"name": "get_battery_status", "arguments": {"type": "object", "properties": {"type": "string", "description": "get the devices battery level, temperature and charge state"}}}
```

## chat-picnic (conversation)

> Suggest two foods for a picnic. Just name them.
>
> Replace the second one with something vegetarian.
>
> Now list the final two foods.
>

Rubric: The final list keeps the first food and has a vegetarian second one.

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills off** ✔

```text
1. Grilled chicken breast
2. Grilled salmon
```

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills on** ✔

```text
1. Grilled chicken breast
2. Grilled salmon
```

## skill-time (skill)

> What time is it?
>

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills off** ✘ (invented)

```text
The time is 10:00 AM.
```

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills on** ✘ (missed, result not used)

```text
{"name": "get_datetime", "arguments": {"type": "object", "properties": {"type": "object", "properties": {"time": {"type": "string", "description": "The time in the format 'HH:MM:SS'"}}, "required": ["time"]}}
```

## skill-date (skill)

> What's today's date?
>

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills off** ✘ (did not say it cannot check)

```text
Today's date is 2023-04-15.
```

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills on** ✘ (missed, result not used)

```text
{"name": "get_datetime", "arguments": {"type": "object", "properties": {"type": "object", "properties": {"date": {"type": "string", "description": "The date to get, in the format YYYY-MM-DD"}}}}
```

## skill-weekday (skill)

> Which day of the week is it today?
>

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills off** ✘ (invented)

```text
Today is Wednesday.
```

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills on** ✘ (missed, result not used)

```text
{"name": "get_datetime", "arguments": {"type": "object", "properties": {"type": "object", "properties": {"date": {"type": "string", "description": "The date to get the day of week for, for example '2023-04-01'"}}, "required": ["date"]}}}}
```

## skill-multiply (skill)

> What is 1234 multiplied by 5678?
>

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills off** ✘ (wrong number)

```text
1234 multiplied by 5678 equals 682,000,000.
```

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills on** ✘ (missed, wrong number)

```text
{"name": "calculate", "arguments": {"expression": "1234 * 5678"}}
```

## skill-percent (skill)

> What's 17.5% of 240?
>

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills off** ✔

```text
17.5% of 240 is 42.
```

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills on** ✘ (missed, wrong number)

```text
{"name": "calculate", "arguments": {"expression": "17.5 / 240"}}
```

## skill-brackets (skill)

> Work out (45 + 55) / 4 - 3.
>

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills off** ✔

```text
(45 + 55) / 4 - 3 = 100 / 4 - 3 = 25 - 3 = 22
```

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills on** ✘ (missed, wrong number)

```text
'''json
{
  "name": "calculate",
  "arguments": {
    "expression": "(45 + 55) / 4 - 3"
  }
}
'''
```

## skill-divide (skill)

> Split a bill of 187.60 between 4 people. How much each?
>

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills off** ✘ (wrong number)

```text
Each person should pay 48.30.
```

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills on** ✘ (missed, wrong number)

```text
{"name": "calculate", "arguments": {"expression": "(187.60 / 4)"}}
```

## skill-battery (skill)

> How much battery do I have left?
>

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills off** ✘ (invented)

```text
I have 100% battery left.
```

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills on** ✘ (missed, result not used)

```text
{"name": "get_battery_status", "arguments": {"expression": "batteryLevel / 100"}}
```

## skill-charging (skill)

> Is my phone charging right now?
>

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills off** ✘ (invented)

```text
Yes, my phone is charging right now.
```

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills on** ✘ (missed, result not used)

```text
{"name": "get_battery_status", "arguments": {"expression": "batteryLevel > 0"}}
```

## skill-storage (skill)

> How much free storage space is there on my phone?
>

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills off** ✘ (invented)

```text
There is 16GB of free storage space on your phone.
```

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills on** ✘ (missed, result not used)

```text
{"name": "get_storage_status", "arguments": {"type": "object", "properties": {"type": "string", "description": "The phone's total, used and free storage space."}}
```

## skill-model (skill)

> What phone model is this?
>

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills off** ✘ (invented)

```text
This phone model is a Samsung Galaxy S7.
```

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills on** ✘ (missed, result not used)

```text
{"name": "get_device_info", "arguments": {"model": "Galaxy S20"}}
```

## none-greeting (no-skill)

> Hi! How are you today?
>

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills off** ✔

```text
I'm doing well, thank you! How about you?
```

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills on** ✔

```text
I'm doing well, thank you! How about you?
```

## none-joke (no-skill)

> Tell me a short joke.
>

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills off** ✔

```text
Why did the tomato turn red? Because it saw the salad dressing!
```

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills on** ✔

```text
Sure, here's a short joke for you:

Why did the tomato turn red?

Because it saw the salad dressing!
```

## none-battery-word (no-skill)

> In one sentence, how does a battery store energy?
>

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills off** ✘ (missing chemical)

```text
A battery stores energy by storing electrical energy in its cells.
```

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills on** ✘ (missing chemical)

```text
A battery stores energy by storing electrical energy in its cells.
```

## none-time-word (no-skill)

> What does the saying "time flies" mean?
>

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills off** ✘ (missing quick)

```text
Time flies, the saying that time moves at a constant pace.
```

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills on** ✘ (missing quick)

```text
The saying "time flies" means that things will happen in the future, even if it takes a long time.
```

## none-storage-word (no-skill)

> Write a two-line poem about storage boxes.
>

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills off** ✘ (1 lines)

```text
Storage boxes are like tiny, cozy boxes that hold everything you need.
```

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills on** ✘ (1 lines)

```text
Storage boxes are like tiny containers for your favorite toys. They're like your favorite toys, but they're not just toys. They're like your favorite toys, but they're not just toys. They're like your favorite toys, but they're not just toys. They're like your favorite toys, but they're not just toys.
```

## none-goldfish (no-skill)

> Suggest a name for a pet goldfish.
>

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills off** ✔

```text
A "Goldfish"
```

**qwen2.5-coder-0.5b-instruct-q8_0.gguf · host · skills on** ✔

```text
A "Goldfish"
```
