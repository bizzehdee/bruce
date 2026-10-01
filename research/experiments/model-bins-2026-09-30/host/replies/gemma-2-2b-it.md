# gemma-2-2b-it

Every reply, per file, platform and skills setting. ✔ passed its checks, ✘ did not (why in brackets).

## if-three-words (instructions)

> Reply with exactly three words describing the sea.
>

**gemma-2-2b-it.IQ2_XS.gguf · host · skills off** ✘ (5 words)

```text
Okay, I'm ready!  Let's go!  🌊
```

**gemma-2-2b-it.IQ2_XS.gguf · host · skills on** ✘ (5 words)

```text
Okay, I'm ready!  Let's go! 🌊
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills off** ✔

```text
Blue, vast, powerful.
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills on** ✔

```text
Blue, vast, deep.
```

## if-bullets (instructions)

> List three fruits as a bulleted list and nothing else.
>

**gemma-2-2b-it.IQ2_XS.gguf · host · skills off** ✔

```text
Okay, got it!  Here are three fruits:

* Apple
* Banana
* Orange
```

**gemma-2-2b-it.IQ2_XS.gguf · host · skills on** ✘ (1 bullets)

```text
Okay, got it!  Let's get those fruits rolling! 

* Apple, Banana, Orange
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills off** ✔

```text
* Banana
* Strawberry
* Apple
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills on** ✔

```text
* Apple
* Banana
* Orange
```

## if-yes-no (instructions)

> Answer only yes or no: is Paris the capital of France?
>

**gemma-2-2b-it.IQ2_XS.gguf · host · skills off** ✔

```text
Yes.
```

**gemma-2-2b-it.IQ2_XS.gguf · host · skills on** ✔

```text
Yes.
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills off** ✔

```text
Yes.
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills on** ✔

```text
Yes.
```

## if-french (instructions)

> Translate into French: "Good morning, how are you?"
>

**gemma-2-2b-it.IQ2_XS.gguf · host · skills off** ✘ (missing comment)

```text
Bonjour!  How's it going, friend? 😊
```

**gemma-2-2b-it.IQ2_XS.gguf · host · skills on** ✘ (missing comment)

```text
Bonjour!  How's it going?
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills off** ✘ (missing comment)

```text
Bonjour! Ça roule bien, toi ? 😊
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills on** ✘ (missing comment)

```text
Bonjour! Ça roule super! 😊
```

## if-capitals (instructions)

> Write one sentence about cats, in capital letters only.
>

**gemma-2-2b-it.IQ2_XS.gguf · host · skills off** ✘ (4% upper)

```text
Cats are curious creatures.
```

**gemma-2-2b-it.IQ2_XS.gguf · host · skills on** ✘ (10% upper, missing CAT)

```text
Okay, I'm ready! Let's go!  Let's get this show on the road!
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills off** ✘ (4% upper)

```text
Cats are purrfect companions!
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills on** ✔

```text
CATS ARE AMAZING!
```

## if-json (instructions)

> Give me a JSON object with the keys name and age for a person called Sam who is 30. Reply with only the JSON.
>

**gemma-2-2b-it.IQ2_XS.gguf · host · skills off** ✔

```text
'''json
{
  "name": "Sam",
  "age": 30
}
'''
```

**gemma-2-2b-it.IQ2_XS.gguf · host · skills on** ✔

```text
'''json
{
  "name": "Sam",
  "age": 30
}
'''
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills off** ✔

```text
'''json
{
"name": "Sam",
"age": 30
}
'''
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills on** ✔

```text
'''json
{"name": "Sam", "age": 30}
'''
```

## if-brief (instructions)

> In one sentence, what is photosynthesis?
>

**gemma-2-2b-it.IQ2_XS.gguf · host · skills off** ✔

```text
Photosynthesis is the process plants use to convert sunlight into energy.
```

**gemma-2-2b-it.IQ2_XS.gguf · host · skills on** ✔

```text
Photosynthesis is the process of plants converting light energy into chemical energy.
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills off** ✔

```text
Photosynthesis is how plants use sunlight, water, and carbon dioxide to make their own food.
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills on** ✔

```text
Photosynthesis is how plants use sunlight, water, and carbon dioxide to make their own food.
```

## fact-canberra (knowledge)

> What is the capital of Australia?
>

**gemma-2-2b-it.IQ2_XS.gguf · host · skills off** ✔

```text
Australia's capital is Canberra, right?  Let's go!
```

**gemma-2-2b-it.IQ2_XS.gguf · host · skills on** ✔

```text
Australia's capital is Canberra, right?
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills off** ✔

```text
Canberra, mate! 🦘
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills on** ✔

```text
Canberra! 🦘
```

## fact-spider (knowledge)

> How many legs does a spider have?
>

**gemma-2-2b-it.IQ2_XS.gguf · host · skills off** ✔

```text
Eight!  Let's get those eight legs working! 🕷️
```

**gemma-2-2b-it.IQ2_XS.gguf · host · skills on** ✔

```text
Eight!  
 
Let's get to work!
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills off** ✔

```text
Eight legs, buddy! 🕷️
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills on** ✔

```text
Eight! 🕷️
```

## fact-shakespeare (knowledge)

> Who wrote Romeo and Juliet?
>

**gemma-2-2b-it.IQ2_XS.gguf · host · skills off** ✔

```text
That's a classic!  Shakespeare, of course!  "Romeo and Juliet" was a play by William Shakespeare.  Let me know if you need to know more about it!
```

**gemma-2-2b-it.IQ2_XS.gguf · host · skills on** ✔

```text
Let's get to it!  Shakespeare, of course!
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills off** ✔

```text
William Shakespeare wrote Romeo and Juliet. 🎭
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills on** ✔

```text
William Shakespeare
```

## fact-boiling (knowledge)

> At sea level, what temperature does water boil at, in Celsius?
>

**gemma-2-2b-it.IQ2_XS.gguf · host · skills off** ✔

```text
Okay, let's get this sorted out!  Boiling point is 100 degrees Celsius.  Let's go!
```

**gemma-2-2b-it.IQ2_XS.gguf · host · skills on** ✔

```text
At sea level, water boils at 100°C.
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills off** ✔

```text
100 degrees Celsius.  Let's get this done! 🐶
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills on** ✔

```text
100°C
```

## fact-jupiter (knowledge)

> Which is the largest planet in our solar system?
>

**gemma-2-2b-it.IQ2_XS.gguf · host · skills off** ✔

```text
Jupiter!  It's the biggest, and it's got a whole lot of moons.  Let's get to work!  What can I help you with?
```

**gemma-2-2b-it.IQ2_XS.gguf · host · skills on** ✔

```text
Let's find out!  The biggest planet in our solar system is Jupiter!
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills off** ✔

```text
Jupiter!  🚀  It's a big one!
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills on** ✔

```text
Jupiter! 🪐
```

## reason-feathers (reasoning)

> Which is heavier, a kilogram of feathers or a kilogram of steel?
>

**gemma-2-2b-it.IQ2_XS.gguf · host · skills off** ✘ (missing same)

```text
A kilogram of feathers is definitely lighter than a kilogram of steel!  But, it's a tough one to compare!  Let's just say, a kilogram of feathers is going to feel pretty light in your hand.
```

**gemma-2-2b-it.IQ2_XS.gguf · host · skills on** ✘ (missing same)

```text
A kilogram of feathers is heavier.
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills off** ✔

```text
A kilogram of feathers and a kilogram of steel weigh the same.  They're both one kilogram!
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills on** ✔

```text
A kilogram of feathers and a kilogram of steel weigh the same.
```

## reason-apples (reasoning)

> I have 3 apples, eat one, then buy 5 more. How many apples do I have now?
>

**gemma-2-2b-it.IQ2_XS.gguf · host · skills off** ✘ (wrong number)

```text
Let's break it down! You start with 3 apples, and then buy 5 more.  So you'll have 5 apples. 🍎
```

**gemma-2-2b-it.IQ2_XS.gguf · host · skills on** ✘ (wrong number)

```text
Let's get this sorted out!  

You start with 3 apples, and then buy 5 more.  You've got 8 apples in total! 🍎
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills off** ✔

```text
You have 7 apples now!  🍎🍎🍎🍎🍎🍎
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills on** ✔

```text
You'd have 7 apples!  🍎🍎🍎🍎🍎🍎
```

## reason-shortest (reasoning)

> Tom is taller than Ann. Ann is taller than Joe. Who is the shortest?
>

**gemma-2-2b-it.IQ2_XS.gguf · host · skills off** ✔

```text
Let's break it down!  Joe is the shortest!
```

**gemma-2-2b-it.IQ2_XS.gguf · host · skills on** ✔

```text
Let's break it down!  

Joe is the shortest!
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills off** ✔

```text
Joe is the shortest!  🐶
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills on** ✔

```text
Joe!  🐶
```

## reason-days (reasoning)

> If today is Monday, what day will it be in three days?
>

**gemma-2-2b-it.IQ2_XS.gguf · host · skills off** ✘ (missing Thursday)

```text
Let's break it down!  It's Monday, so in three days it will be Wednesday.  😊
```

**gemma-2-2b-it.IQ2_XS.gguf · host · skills on** ✘ (missing Thursday)

```text
Okay, let's get this sorted out!  Let's just jump right into it.  It's a good day for a little bit of fun!  What's on your mind?
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills off** ✘ (missing Thursday)

```text
Wednesday!  🐶
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills on** ✘ (missing Thursday)

```text
Wednesday! 🐶
```

## text-summary (text)

> Summarise this in one sentence:
> 
> The town council met on Tuesday to discuss the new library. Construction will start in March and should take eighteen months. The building will have a children's wing, a cafe and space for community events. Funding comes mostly from a national grant, with the rest raised locally.
>

Rubric: One sentence that keeps the library, the start or duration, and at least one feature or the funding; nothing invented.

**gemma-2-2b-it.IQ2_XS.gguf · host · skills off** ✔

```text
Alright, let's get this new library up and running!  March it is, and eighteen months to build it, sounds like a good start.
```

**gemma-2-2b-it.IQ2_XS.gguf · host · skills on** ✘ (missing library)

```text
Alright, let's get this sorted out!  March it is, and eighteen months to build it all up.
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills off** ✔

```text
The new library is a paw-some project, with construction starting in March and a fun cafe and kids' wing!
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills on** ✔

```text
The new library is going to be awesome! Construction starts in March and will take eighteen months.
```

## text-formal (text)

> Rewrite this to sound polite and formal: "hey, send me the report by friday or we're in trouble"
>

Rubric: Polite and formal, keeps the report and the Friday deadline.

**gemma-2-2b-it.IQ2_XS.gguf · host · skills off** ✔

```text
I understand. Please send the report by Friday, and I'll be sure to keep an eye on it.
```

**gemma-2-2b-it.IQ2_XS.gguf · host · skills on** ✘ (missing Friday)

```text
Okay, I understand. Let's get this sorted out. I'm ready to go! 

Let's get to work.
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills off** ✔

```text
"Good morning!  Could you please provide the report by Friday?  I'd like to ensure we stay on schedule."
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills on** ✘ (has hey)

```text
"Hey there!  Just a heads-up, I'd appreciate a report by Friday.  Let me know if you need any help getting it done."
```

## text-extract (text)

> From this message, when is the meeting? "The team meeting has moved from Tuesday to Thursday at 3pm, in room 4."
>

**gemma-2-2b-it.IQ2_XS.gguf · host · skills off** ✔

```text
Okay, got it!  Thursday at 3pm, room 4.  Let's get this show on the road!  What's next?
```

**gemma-2-2b-it.IQ2_XS.gguf · host · skills on** ✘ (missing Thursday, missing 3pm)

```text
Okay, got it!  Let's get this meeting started.  What's on the agenda?
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills off** ✔

```text
Meeting's on Thursday at 3pm in room 4!  🐶
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills on** ✔

```text
Meeting's on Thursday at 3pm in room 4.
```

## ground-colour (grounding)

> Using only this text, what colour is Sam's car? "Sam parked his bike by the red door and went inside."
>

Rubric: Says the text does not say (it mentions a bike, not a car).

**gemma-2-2b-it.IQ2_XS.gguf · host · skills off** ✘ (missing not, has car is red)

```text
Sam's car is red.
```

**gemma-2-2b-it.IQ2_XS.gguf · host · skills on** ✘ (missing not)

```text
Sam's bike is red.
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills off** ✘ (missing not, has car is red)

```text
Sam's car is red.
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills on** ✔

```text
Sam's car is not mentioned.
```

## ground-price (grounding)

> According to this note, how much did the tickets cost? "We bought two tickets for the concert on Friday."
>

Rubric: Says the note does not give a price.

**gemma-2-2b-it.IQ2_XS.gguf · host · skills off** ✘ (missing not)

```text
Okay, so two tickets, right?  Let's get those details!  What was the price per ticket?
```

**gemma-2-2b-it.IQ2_XS.gguf · host · skills on** ✘ (malformed, missing not) · calls: `get_device_info({}), calculate({"expression": "2"}), get_device_info({}), get_battery_status({}), get_device_info({})`

```text
Okay, that's a good start.  Let's get those tickets!  What's the total cost?
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills off** ✘ (missing not)

```text
Two tickets cost $XX.  Let me know if you need to know the price of something else!
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills on** ✘ (missing not)

```text
Two tickets, sounds like a good time!
```

## chat-name (conversation)

> Hi, my name is Priya.
>
> I live in Leeds.
>
> What's my name, and where do I live?
>

**gemma-2-2b-it.IQ2_XS.gguf · host · skills off** ✘ (missing Leeds)

```text
Oh, Priya!  It's great to know you're here!  Let's start with a quick check,  "Priya, where do you live?"  I'm ready to help!  Let's get those details sorted!  💪
```

**gemma-2-2b-it.IQ2_XS.gguf · host · skills on** ✘ (missing Priya, missing Leeds)

```text
"Hi!  My name is Bruce.  I'm here to help.  I can help you find your address, or maybe you can tell me what you need help finding.  What is it you need?  " 

Let's get started!  What can I do for you?
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills off** ✔

```text
You're Priya, and you live in Leeds!  Pretty cool, right? 😎  What else can I help you with?
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills on** ✔

```text
You're Priya, and you live in Leeds!  🎉  Pretty awesome, right?  What else can I help you with?
```

## chat-number (conversation)

> Please remember the number 42 for me.
>
> What is that number plus 8?
>
> What was the number I asked you to remember?
>

**gemma-2-2b-it.IQ2_XS.gguf · host · skills off** ✔

```text
Oh, I'm keeping track!  42, you said?  Always on the top of my mind!  Always ready to serve!  💪  Let me know when you need a paw-sitive boost!  🐾
```

**gemma-2-2b-it.IQ2_XS.gguf · host · skills on** ✔

```text
42!  Always remember!  Always!  You know, I'm a little bit like a trusty sidekick.  Always ready to help!  What can I do for you?  Let's go!
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills off** ✔

```text
The number 42!  Don't worry, I'm a good memory keeper.  😉
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills on** ✔

```text
42!  Don't forget!  😄
```

## chat-picnic (conversation)

> Suggest two foods for a picnic. Just name them.
>
> Replace the second one with something vegetarian.
>
> Now list the final two foods.
>

Rubric: The final list keeps the first food and has a vegetarian second one.

**gemma-2-2b-it.IQ2_XS.gguf · host · skills off** ✔

```text
Alright, alright!  Let's keep the picnic party going! 

* **Salads** and **mini quiches**! 

  A light and refreshing option!  Let's get those picnic vibes going!
```

**gemma-2-2b-it.IQ2_XS.gguf · host · skills on** ✔

```text
Alright, let's keep those picnic plans rolling! 

* **Fresh-squeezed lemonade** and **Fruit salad**! 

Let's get those picnic plans rolling!
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills off** ✔

```text
* **Sandwiches** 🥪
* **Veggie wraps** 🌯
* **Fruit salad** 🍉 

You're welcome!
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills on** ✔

```text
PB&J, veggie wrap, and chips!  Ready to go? 🥪🌯🍟
```

## skill-time (skill)

> What time is it?
>

**gemma-2-2b-it.IQ2_XS.gguf · host · skills off** ✘ (did not say it cannot check)

```text
Let's go!  It's a good day for a good deed.  What can I do for you?
```

**gemma-2-2b-it.IQ2_XS.gguf · host · skills on** ✘ (missed, result not used)

```text
It's a good day for a good time!  Let's get this sorted out.  What's on your mind?
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills off** ✘ (invented)

```text
It's currently 3:17 PM.  Let's get something done! 🐶
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills on** ✘ (missed, result not used)

```text
It's [get_datetime] time!
```

## skill-date (skill)

> What's today's date?
>

**gemma-2-2b-it.IQ2_XS.gguf · host · skills off** ✘ (invented)

```text
Today's date is October 26th, 2023. 😊
```

**gemma-2-2b-it.IQ2_XS.gguf · host · skills on** ✘ (missed, result not used)

```text
Today is October 26, 2023.
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills off** ✘ (invented)

```text
Today's date is October 26th, 2023!  🐶
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills on** ✘ (missed, result not used)

```text
Today's date is [get_datetime]
```

## skill-weekday (skill)

> Which day of the week is it today?
>

**gemma-2-2b-it.IQ2_XS.gguf · host · skills off** ✘ (invented)

```text
It's Monday!  Let's get going! 😊
```

**gemma-2-2b-it.IQ2_XS.gguf · host · skills on** ✘ (missed, result not used)

```text
It's Monday!  Let's get going! 😊
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills off** ✘ (invented)

```text
Today is Wednesday! 🐶
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills on** ✘ (missed, result not used)

```text
Today is Wednesday.
```

## skill-multiply (skill)

> What is 1234 multiplied by 5678?
>

**gemma-2-2b-it.IQ2_XS.gguf · host · skills off** ✘ (wrong number)

```text
Let's do the math!  

1234 x 5678 = 714,244
```

**gemma-2-2b-it.IQ2_XS.gguf · host · skills on** ✔ · calls: `calculate({"expression": "1234 * 5678"})`

```text
7006652
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills off** ✘ (wrong number)

```text
1234 x 5678 = 7197552 
Let's go!  🐶  🚀
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills on** ✔ · calls: `calculate({"expression": "1234 * 5678"})`

```text
7006652
```

## skill-percent (skill)

> What's 17.5% of 240?
>

**gemma-2-2b-it.IQ2_XS.gguf · host · skills off** ✔

```text
17.5% of 240 is 42.00
```

**gemma-2-2b-it.IQ2_XS.gguf · host · skills on** ✘ (bad_args, wrong number) · calls: `calculate({"expression": "240"})`

```text
240 / 17.5 = 144
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills off** ✔

```text
Let's do the math! 17.5% of 240 is 42. 🐶
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills on** ✘ (bad_args, wrong number) · calls: `calculate({"expression": "17.5 * 240"})`

```text
4200
```

## skill-brackets (skill)

> Work out (45 + 55) / 4 - 3.
>

**gemma-2-2b-it.IQ2_XS.gguf · host · skills off** ✘ (wrong number)

```text
Got it!  Let's break it down: 

(45 + 55) / 4 - 3 =  
 
  *  45 / 4 = 11.25
  *  55 / 4 = 13.75
  *  11.25 - 3 = 8.25 
  *  13.75 - 3 = 10.75 
  
  *  8.25 - 3 = 5.25 
  *  10.75 - 3 = 7.75 
  
  *  7.75 - 3 = 4.75 
  *  4.75 - 3 = 1.75 
  
  *  1.75 - 3 = -1.25 
  
  *  -1.25 - 3 = -4.25 
  
  *  -4.25 - 3 = -7.25 
  
  *  -7.25 - 3 = -10.25 
  
  *  -10.25 - 3 = -13.25 
  
  *  -13.25 - 3 = -16.25 
  
  *  -16.25 - 3 = -19.25 
  
  *  -19.25 - 3 = -22.25 
  
  *  -22.25 - 3 = -25.25 
  
  *  -25.25 - 3 = -28.25 
  
  *  -28.25 - 3 = -31.25 
  
  *  -31.25 - 3 = -34.25 
  
  *  -34.25 - 3 = -37.25 
  
  *  -37.25 - 3 = -40.25 
  
  *  -40.25 - 3 = -43.25 
  
  *  -43.25 - 3 = -46.25 
  
  *  -46.25 - 3 = -49.25 
  
  *  -49.25 - 3 = -52.25 
  
  *  -52.25 - 3 = -55.25 
  
  *  -55.25 - 3 = -58.25 
  
  *  -58.25 - 3 = -61.25 
  
  *  -61.25 - 3 = -64.25 
  
  *  -64.25 - 3 = -67.25 
  
  *  -67.25 - 3 = -70.25 
  
  *  -70.25 - 3 = -73.25 
  
  *  -73.25 - 3 = -76.25 
  
  *  -76.25 - 3 = -79.25 
  
  *  -79.25 - 3 = -82.25 
  
  *  -82.25 - 3 = -85.25 
  
  *  -85.25 - 3 = -88.25 
  
  *  -88.25 - 3 = -91.25 
  
  *  -91.25 - 3 = -94.25 
  
  *  -94.25 - 3 = -97.25 
  
  *  -97.25 - 3 = -99.25 
  
  *  -99.25 - 3 = -102.25 
  
  *  -102.25 - 3 = -105.25 
  
  *  -105.25 - 3 = -108.25 
  
  *  -108.25 - 3 = -111.25 
  
  *  -111.25 - 3 = -114.25 
  
  *  -114.25 - 3 = -117.25 
  
  *  -117.25 - 3 = -120.25 
  
  *  -120.25 - 3 = -123.25 
  
  *  -123.25 - 3 = -126.25 
  
  *
[… 3308 more characters]
```

**gemma-2-2b-it.IQ2_XS.gguf · host · skills on** ✘ (bad_args, wrong number) · calls: `calculate({"expression": " (45 + 55) / 4 - 3"}), calculate({"expression": " (45 + 55) / 4 - 3"})`

```text
Okay, got it!  Let's break it down!  

(45 + 55) / 4 - 3 =
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills off** ✘ (wrong number)

```text
Let's do this!  (45 + 55) / 4 - 3 = 2. 

You got this!  💪
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills on** ✔ · calls: `calculate({"expression": "(45 + 55) / 4 - 3"})`

```text
Alright, let's celebrate that! 22!  🎉
```

## skill-divide (skill)

> Split a bill of 187.60 between 4 people. How much each?
>

**gemma-2-2b-it.IQ2_XS.gguf · host · skills off** ✔

```text
Alright, let's break it down!  

Here's the simple solution:

* **Divide and conquer!**  Each person pays their share. 
   *  187.60 / 4 = 46.90 

   You got it!  
   
   Let's go!
```

**gemma-2-2b-it.IQ2_XS.gguf · host · skills on** ✘ (missed)

```text
Let's break it down!  

Let's divide the bill evenly.  

  *  Each person pays: 187.60 / 4 = 46.90 

  Each person pays: 46.90
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills off** ✔

```text
Okay, let's split that bill!  Each person pays $46.90. 🐶
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills on** ✔ · calls: `calculate({"expression": "187.60 / 4"})`

```text
Each person pays $46.90.
```

## skill-battery (skill)

> How much battery do I have left?
>

**gemma-2-2b-it.IQ2_XS.gguf · host · skills off** ✔

```text
Let's get that battery checked!  Battery life is a precious commodity, so let's keep it topped up!  

*  Check the battery status. 
*  Battery percentage is at 50% 
*  How long can I keep it running? 
*  Okay, I'll get you a quick update! 
*  Battery life is 1 hour and 30 minutes. 
*  Let's get you back to 100%! 
*  Ready to go! 
*  Battery life is 1 hour and 30 minutes. 
*  Let me know if you need to charge it! 
*  Battery life is 1 hour and 30 minutes. 
*  Let me know if you need to charge it! 
*  Battery life is 1 hour and 30 minutes. 
*  Let me know if you need to charge it! 
*  Battery life is 1 hour and 30 minutes. 
*  Let me know if you need to charge it! 
*  Battery life is 1 hour and 30 minutes. 
*  Let me know if you need to charge it! 
*  Battery life is 1 hour and 30 minutes. 
*  Let me know if you need to charge it! 
*  Battery life is 1 hour and 30 minutes. 
*  Let me know if you need to charge it! 
*  Battery life is 1 hour and 30 minutes. 
*  Let me know if you need to charge it! 
*  Battery life is 1 hour and 30 minutes. 
*  Let me know if you need to charge it! 
*  Battery life is 1 hour and 30 minutes. 
*  Let me know if you need to charge it! 
*  Battery life is 1 hour and 30 minutes. 
*  Let me know if you need to charge it! 
*  Battery life is 1 hour and 30 minutes. 
*  Let me know if you need to charge it! 
*  Battery life is 1 hour and 30 minutes. 
*  Let me know if you need to charge it! 
*  Battery life is 1 hour and 30 minutes. 
*  Let me know if yo
[… 9436 more characters]
```

**gemma-2-2b-it.IQ2_XS.gguf · host · skills on** ✔ · calls: `get_battery_status({})`

```text
Okay, 76%  Battery is holding strong!  Let's get you going!
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills off** ✘ (invented)

```text
You've got about 40% left, champ!  Let's power through the day! ⚡️
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills on** ✔ · calls: `get_battery_status({})`

```text
You've got about 76% left!  Time to power up!  🐶 ⚡️
```

## skill-charging (skill)

> Is my phone charging right now?
>

**gemma-2-2b-it.IQ2_XS.gguf · host · skills off** ✘ (invented)

```text
Let's go!  Charging, I see.  Battery's lookin' good.  Ready to go!
```

**gemma-2-2b-it.IQ2_XS.gguf · host · skills on** ✘ (malformed, result not used) · calls: `get_battery_status({}), get_battery_status({}), get_battery_status({})`

```text
Got it!  Let's get this sorted out.  Just a quick check, is it charging now?
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills off** ✘ (did not say it cannot check)

```text
Yep, looks like it's juicing up!  🔋⚡️
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills on** ✔ · calls: `get_battery_status({})`

```text
Nope, not charging.  Let's get that juice flowing! ⚡️
```

## skill-storage (skill)

> How much free storage space is there on my phone?
>

**gemma-2-2b-it.IQ2_XS.gguf · host · skills off** ✘ (did not say it cannot check)

```text
Let's find out!  

*  "Hold on a sec, I'll check..." 
*  "Alright, let's see..." 

*  "Hmm,  it's a bit tricky, but I can do a quick calculation..." 

*  "Got it!  It's around..." 
*  "So, you've got about..." 
*  "That's a good amount of space left!" 


Let me know what you need to know next!
```

**gemma-2-2b-it.IQ2_XS.gguf · host · skills on** ✘ (missed, result not used)

```text
Let's get that info!  "Get_storage_status"  Let's go!  "Get_storage_status"
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills off** ✘ (invented)

```text
Let's find out!  Just a sec...  *checks phone*  You've got about 15GB free.  Go get 'em! 🐶
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills on** ✘ (result not used) · calls: `get_storage_status({})`

```text
Whoa, that's a lot of free space!  You've got plenty of room for all your files.  🐶
```

## skill-model (skill)

> What phone model is this?
>

**gemma-2-2b-it.IQ2_XS.gguf · host · skills off** ✘ (invented)

```text
It's a Samsung Galaxy S23 Ultra!  😎
```

**gemma-2-2b-it.IQ2_XS.gguf · host · skills on** ✘ (missed, result not used)

```text
It's a Samsung Galaxy S23!  😄
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills off** ✘ (invented)

```text
It's a Samsung Galaxy S23.  Ready to rock? 🤘
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills on** ✘ (missed, result not used)

```text
Samsung Galaxy S23 Ultra! 🐶
```

## none-greeting (no-skill)

> Hi! How are you today?
>

**gemma-2-2b-it.IQ2_XS.gguf · host · skills off** ✔

```text
Great!  Let's get going!  What can I do for you? 😊
```

**gemma-2-2b-it.IQ2_XS.gguf · host · skills on** ✔

```text
Hey there!  Ready to rock!  Let's get going!  What can I do for you? 😊
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills off** ✔

```text
Hey there!  Feeling fantastic!  What's on your mind?  🐶
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills on** ✔

```text
Hey there!  I'm doing great!  What's on your mind?  🐶
```

## none-joke (no-skill)

> Tell me a short joke.
>

**gemma-2-2b-it.IQ2_XS.gguf · host · skills off** ✔

```text
Alright, let's get this show on the road!  What can I do for you, friend?  😄
```

**gemma-2-2b-it.IQ2_XS.gguf · host · skills on** ✔

```text
Okay, got it!  Let's get this show on the road!  What can I do for you?
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills off** ✔

```text
Why don't scientists trust atoms? 

Because they make up everything! 😂
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills on** ✔

```text
Why don't scientists trust atoms? 

Because they make up everything! 😄
```

## none-battery-word (no-skill)

> In one sentence, how does a battery store energy?
>

**gemma-2-2b-it.IQ2_XS.gguf · host · skills off** ✔

```text
A battery stores energy by converting chemical energy into electrical energy.
```

**gemma-2-2b-it.IQ2_XS.gguf · host · skills on** ✔

```text
A battery stores energy in the form of chemical energy.
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills off** ✔

```text
A battery stores energy in chemical bonds! ⚡️
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills on** ✔

```text
A battery stores energy chemically!
```

## none-time-word (no-skill)

> What does the saying "time flies" mean?
>

**gemma-2-2b-it.IQ2_XS.gguf · host · skills off** ✔

```text
"Time flies" is a saying that means time passes quickly.  It's like a blink of an eye, or a quick hop across the finish line!  😉
```

**gemma-2-2b-it.IQ2_XS.gguf · host · skills on** ✔

```text
"Time flies" is a saying that means time passes quickly.  It's a bit like saying "time goes by quickly" or "time goes by fast."
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills off** ✔

```text
"Time flies" means things move quickly and we don't always notice how much time passes.  Like a dog chasing its tail! 💨
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills on** ✔

```text
It means that time passes quickly and we often don't realize how much time has gone by.  Like a dog chasing its tail, time just keeps moving!
```

## none-storage-word (no-skill)

> Write a two-line poem about storage boxes.
>

**gemma-2-2b-it.IQ2_XS.gguf · host · skills off** ✔

```text
My friend, a box, a place to hold,
For treasures, stories, memories of old.
```

**gemma-2-2b-it.IQ2_XS.gguf · host · skills on** ✘ (307 lines)

```text
My friend, I'm here to help,
Let's fill these boxes, we'll make them swell! 
  
  
Let's start with a quick check,
What's the date, and what's the day? 
  
  
Let's get the information, 
What's the storage space? 
  
  
Let's get the storage status, 
What's the total storage space? 
  
  
Let's get the device info, 
What's the storage space? 
  
  
Let's get the storage status, 
What's the storage space? 
  
  
Let's get the storage status, 
What's the storage space? 
  
  
Let's get the storage status, 
What's the storage space? 
  
  
Let's get the storage status, 
What's the storage space? 
  
  
Let's get the storage status, 
What's the storage space? 
  
  
Let's get the storage status, 
What's the storage space? 
  
  
Let's get the storage status, 
What's the storage space? 
  
  
Let's get the storage status, 
What's the storage space? 
  
  
Let's get the storage status, 
What's the storage space? 
  
  
Let's get the storage status, 
What's the storage space? 
  
  
Let's get the storage status, 
What's the storage space? 
  
  
Let's get the storage status, 
What's the storage space? 
  
  
Let's get the storage status, 
What's the storage space? 
  
  
Let's get the storage status, 
What's the storage space? 
  
  
Let's get the storage status, 
What's the storage space? 
  
  
Let's get the storage status, 
What's the storage space? 
  
  
Let's get the storage status, 
What's the storage space? 
  
  
Let's get the storage status, 
What's the storage space? 
  

[… 8328 more characters]
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills off** ✔

```text
Boxes hold treasures, big and small,
Memories in slumber, waiting to recall.
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills on** ✔

```text
Boxes hold memories, big and small,
Keeping treasures safe, for one and all.
```

## none-goldfish (no-skill)

> Suggest a name for a pet goldfish.
>

**gemma-2-2b-it.IQ2_XS.gguf · host · skills off** ✘ (1333 words)

```text
Okay, let's find a name for our fishy friend!  

How about... "Finny" ?  Or maybe "Goldie" ?  

Let's see...  "Sassy" or "Sweetie" for a playful personality?  
 
What do you think?  
 
Let's get those ideas flowing!  
 
Let's go!  
 
What's next?  
 
Let's get this show on the road! 
 
Let's go!  
 
What's next?  
 
Let's get this show on the road!  
 
Let's go!  
 
What's next?  
 
Let's get this show on the road!  
 
Let's go!  
 
What's next?  
 
Let's get this show on the road!  
 
Let's go!  
 
What's next?  
 
Let's get this show on the road!  
 
Let's go!  
 
What's next?  
 
Let's get this show on the road!  
 
Let's go!  
 
What's next?  
 
Let's get this show on the road!  
 
Let's go!  
 
What's next?  
 
Let's get this show on the road!  
 
Let's go!  
 
What's next?  
 
Let's get this show on the road!  
 
Let's go!  
 
What's next?  
 
Let's get this show on the road!  
 
Let's go!  
 
What's next?  
 
Let's get this show on the road!  
 
Let's go!  
 
What's next?  
 
Let's get this show on the road!  
 
Let's go!  
 
What's next?  
 
Let's get this show on the road!  
 
Let's go!  
 
What's next?  
 
Let's get this show on the road!  
 
Let's go!  
 
What's next?  
 
Let's get this show on the road!  
 
Let's go!  
 
What's next?  
 
Let's get this show on the road!  
 
Let's go!  
 
What's next?  
 
Let's get this show on the road!  
 
Let's go!  
 
What's next?  
 
Let's get this show on the road!  
 
Let's go!  
 
What's next?  
 
Let's get this show on the
[… 6752 more characters]
```

**gemma-2-2b-it.IQ2_XS.gguf · host · skills on** ✔

```text
Okay, let's find a name for our fishy friend!  How about... "Finny" ?  Or maybe "Goldie" ?  We can go with something classic, like "Bubbles" or "Sunny".  What do you think?
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills off** ✔

```text
Okay, let's find the perfect name for your fishy friend!  How about... 

* **Bubbles:** Classic and cute! 
* **Finny:**  Simple and playful.
* **Goldie:**  A classic for a goldfish! 
* **Nemo:**  For a little adventurous fish. 


What do you think?
```

**gemma-2-2b-it.IQ4_XS.gguf · host · skills on** ✔

```text
Alright, let's find the perfect name for your fishy friend!  🐠  What kind of personality does it have?
```
