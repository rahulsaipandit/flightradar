I’ve got SmolLM2‑360M running on a Samsung Galaxy Watch 4 Classic (about 380MB free RAM) by tweaking llama.cpp and the underlying ggml memory model. By default, the model was being loaded twice in RAM: once via the APK’s mmap page cache and again via ggml’s tensor allocations, peaking at 524MB for a 270MB model.

The fix: I pass host_ptr into llama_model_params, so CPU tensors point directly into the mmap region and only Vulkan tensors are copied. On real hardware this gives:

Peak RAM: 524MB → 142MB (74% reduction)

First boot: 19s → 11s

Second boot: ~2.5s (mmap + KV cache warm)

Code:
https://github.com/Perinban/llama.cpp/tree/axon‑dev

Longer write‑up with VmRSS traces and design notes:
https://www.linkedin.com/posts/perinban-parameshwaran_machinelearning-llm-embeddedai-activity-7445374117987373056-xDj9?utm_source=share&utm_medium=member_desktop&rcm=ACoAAA1J2KoBHgKFnrEIUchmbOoZTpAqKKxKK7o

I’m planning a PR to ggml‑org/llama.cpp; feedback on the host‑ptr / mmap pattern is welcome.


Upvote
40

Downvote

19
Go to comments

Repost

Share
u/OpenAI avatar
OpenAI
•
Ad

When you are the team, ChatGPT Work helps you do more.
Learn More
chatgpt.com
Clickable image which will reveal the video player: When you are the team, ChatGPT Work helps you do more.
Collapse video player

0:00 / 0:00




Join the conversation

Sort by:

Top

Search Comments
Expand comment search
Comments Section
MustBeSomethingThere
•
5mo ago
https://huggingface.co/LiquidAI/LFM2.5-350M-GGUF would be better than SmolLM2


Upvote
9

Downvote

Reply

Award

Share

RecognitionFlat1470
OP
•
5mo ago
Thanks! Currently stuck on the ASR side though. Once the internals are done it's just a one line change to swap the model, so will definitely test it out then.


Upvote
3

Downvote

Reply

Award

Share

u/dinerburgeryum avatar
dinerburgeryum
•
5mo ago
emoji:Discord:
You’re a madperson and a credit to this community. 👍


Upvote
6

Downvote

Reply

Award

Share

u/-p-e-w- avatar
-p-e-w-
•
5mo ago
emoji:Discord:
Profile Badge for the Achievement Top 1% Commenter Top 1% Commenter
Samsung Galaxy Watch 4 Classic (about 380MB free RAM)

In 2026, a watch has 380 Megabytes of free RAM. Think about that for a moment.

My first computer had 80 Megabytes of total hard drive space. That was a desktop PC that weighed about 10 kg.


Upvote
5

Downvote

Reply

Award

Share

u/Party-Special-5177 avatar
Party-Special-5177
•
5mo ago
lol for some reason I had imagined you were much younger. Windows or actual DOS?

My first computer was a dell dimension and was well into the GB era lol.


Upvote
1

Downvote

Reply

Award

Share

u/-p-e-w- avatar
-p-e-w-
•
5mo ago
emoji:Discord:
Profile Badge for the Achievement Top 1% Commenter Top 1% Commenter
Windows 3.1, which was basically a DOS shell.


Upvote
2

Downvote

Reply

Award

Share

WhoRoger
•
5mo ago
Are you the p-e-w that makes those Heretics? Nice.


Upvote
1

Downvote

Reply

Award

Share

u/-p-e-w- avatar
-p-e-w-
•
5mo ago
emoji:Discord:
Profile Badge for the Achievement Top 1% Commenter Top 1% Commenter
Indeed.


Upvote
1

Downvote

Reply

Award

Share

WhoRoger
•
5mo ago
Thanks for that! Heretics are precious. Pretty sure I downloaded a couple of yours this week.


Upvote
1

Downvote

Reply

Award

Share

u/Juicebox-PeopleGPT avatar
u/Juicebox-PeopleGPT
•
Ad

Source the best candidates, faster. Get started for free.
Learn More
juicebox.ai
Clickable image which will reveal the video player: Source the best candidates, faster. Get started for free. 
Collapse video player

0:00 / 0:00




cptbeard
•
5mo ago
cool but do you actually have some usecase for LLM on a watch? getting a decent ASR to run would seem like it'd have more uses.


Upvote
1

Downvote

Reply

Award

Share

RecognitionFlat1470
OP
•
5mo ago
Still working on that. Since ASR isn't exposed on Wear OS, I'm building my own. Tried SenseVoice initially, but supporting multiple OS targets got messy fast, so I'm still looking for a cleaner approach.

For now I've got something working with CMUdict and a phoneme decoder, though there are some speed issues and limitations I'm working through one by one. The main problem is that proper phoneme decoding really needs a model loaded first.


Upvote
1

Downvote

Reply

Award

Share

WhoRoger
•
5mo ago
Wait, loaded twice? Is that general behavior or just specific to arm or Android or this model or what?

I don't really get what's what here, but I'm curious to see what llama.cpp devs will say about that.


Upvote
1

Downvote

Reply

Award

Share

RecognitionFlat1470
OP
•
5mo ago
It's not ARM, not Android, not this model. It's just how llama.cpp works for everyone.

When you open a file on your phone, the phone reads it and puts it into memory so it can use it. Simple. But llama.cpp does something weird — it reads the model file into memory, AND then makes a full second copy of it in memory so the GPU can use it. For a tiny moment, you have two full copies sitting there at the same time.

On a laptop with 16GB of memory, nobody cares. On my watch with 380MB free? Dead. Every time.

The fix is simple. For the parts the CPU needs, just point at the original file — don't copy it at all. For the parts the GPU needs, you still have to copy, but the moment the copy is done you throw away the original pages straight away. So you never have two full copies alive at the same time.

270MB model. Was eating 524MB. Now 142MB. Nothing changed except we stopped making a pointless copy.


Upvote
1

Downvote

Reply

Award

Share

WhoRoger
•
5mo ago
It's not that I don't believe you... I personally use Ollama so maybe there's some difference there, but I've been planning to move to llama.cpp, so I've been reading up on it, and I'd think such a spike in RAM use would be noticed and at least wildly reported. I think people do care about system RAM a lot. I dunno but it's bizarre, so you can imaging my confusion.

Maybe there's some caching on other OS's that makes this irrelevant? I doubt that, but can't think of another reason why this wouldn't be obvious. That's why I'm curious what the devs will say. Please keep us posted


Upvote
1

Downvote

Reply

Award

Share

RecognitionFlat1470
OP
•
5mo ago
That confusion makes total sense.

The reason nobody noticed is that Linux and Mac hide it from you. When you check RAM usage in any tool, the model file pages do not show up as your app using RAM. They show up as "cached" which looks like free memory. So people see normal numbers and think everything is fine. The waste is there but invisible.

Android does not do this. If something is in RAM it counts. No hiding. So the same code that looks fine on desktop kills the app on Android instantly.

Desktop users are paying the cost but cannot see the bill.

The watch is actually the most honest environment to test this. Nothing gets hidden. Every byte counts and gets counted. That is why we caught it here and nobody caught it on desktop despite it happening to everyone.


Upvote
1

Downvote

Reply

Award

Share

WhoRoger
•
5mo ago
That makes some sense...

But well my own setup - assuming Ollama behaves the same... I run models just on CPU (or Vulkan) on Fedora with 32GB system RAM, but I also have zram set up and no physical swap. I can run some 20GB worth of models and still have memory for a lot of other stuff. Some of that other stuff compresses well so it can get pushed off to zram, but I think LLMs aren't very compressible. So if those 20GB worth of models would take up 40GB, that... I'd probably notice, because the system would collapse, lol.

So I guess it really is cache on Linux in the sense that it's not really physically necessary, or is recognised as duplicated data and ignored. I don't know how it works on system level, but I can't explain it otherwise, unless Ollama does something different. I doubt that, since it's just a wrapper.

But yea, still better to not do that. And good to know, haha.