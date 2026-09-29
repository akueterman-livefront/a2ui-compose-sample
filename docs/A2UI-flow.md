# A2UI Flow Diagram
A2UI works by defining & sending the A2UI JSON schema with each request, forcing constraints on the LLM to return data in a certain structure that can be used to render real native components.

<img width="1587" height="888" alt="Screenshot 2026-09-29 at 12 07 47 PM" src="https://github.com/user-attachments/assets/708367c8-df87-41e5-96bc-8fd3fc2f200d" />

Part of that prompt data is the catalog of possible UI elements that can be 'rendered' by the LLM.

<img width="1576" height="872" alt="Screenshot 2026-09-29 at 12 10 33 PM" src="https://github.com/user-attachments/assets/c66be818-749b-4a0e-bf04-a8bf1ac8b674" />

This is important for calculating cost / feasibility of smaller models or on-device AI. For example A2UI instructions is about ~12k tokens, compared to the ~4k Gemini Nano context, input + output.
