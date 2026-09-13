// Assessment answer keys exist on the server only. Content requires site safety-officer approval before deployment.
export const modules = [
  {
    id: "fire",
    title: "Fire & Explosion Response",
    minutes: 12,
    version: 1,
    description:
      "Recognize a simulated fire, protect your escape route, and practice the PASS sequence.",
    equipment:
      "Training area approved by your supervisor. Never light a real fire.",
    steps: [
      {
        title: "Identify the exit",
        instruction:
          "Keep a clear escape route behind you. Tap the green exit marker.",
        target: "exit",
      },
      {
        title: "Raise the alarm",
        instruction:
          "Alert the control room and evacuate people. Tap the alarm marker.",
        target: "alarm",
      },
      {
        title: "Choose the equipment",
        instruction:
          "For this simulated energized electrical cabinet, select the labelled CO₂ extinguisher. Follow site-specific instructions.",
        target: "extinguisher",
      },
      {
        title: "Pull",
        instruction: "Pull the safety pin on the simulated extinguisher.",
        target: "pin",
      },
      {
        title: "Aim",
        instruction:
          "Aim the nozzle at the base of the simulated fire while keeping an escape route.",
        target: "base",
      },
      {
        title: "Squeeze",
        instruction: "Squeeze the handle on the simulated extinguisher.",
        target: "handle",
      },
      {
        title: "Sweep",
        instruction:
          "Drag across the labelled Sweep marker to practice sweeping across the base of the simulated fire.",
        target: "sweep",
      },
      {
        title: "Evacuate",
        instruction:
          "Withdraw immediately if conditions become unsafe. Tap the exit to finish the simulation.",
        target: "exit",
      },
    ],
    questions: [
      {
        id: "f1",
        topic: "Escape route",
        text: "Before attempting to tackle a small fire, what must you have?",
        options: [
          "A clear escape route and site authorization",
          "A closed exit",
          "A phone recording",
        ],
        answer: 0,
      },
      {
        id: "f2",
        topic: "Equipment selection",
        text: "In this electrical-cabinet scenario, which labelled extinguisher was selected?",
        options: [
          "Water bucket",
          "CO₂ extinguisher approved for the scenario",
          "Any unlabelled cylinder",
        ],
        answer: 1,
      },
      {
        id: "f3",
        topic: "PASS sequence",
        text: "What is the correct PASS order?",
        options: [
          "Sweep, pull, aim, squeeze",
          "Pull, aim, squeeze, sweep",
          "Aim, sweep, pull, squeeze",
        ],
        answer: 1,
      },
      {
        id: "f4",
        topic: "Evacuation",
        text: "Smoke is spreading and your exit is threatened. What should you do?",
        options: [
          "Continue the drill near the fire",
          "Retrieve personal belongings",
          "Withdraw, raise the alarm and follow the site emergency plan",
        ],
        answer: 2,
      },
      {
        id: "f5",
        topic: "Aim",
        text: "Where do you aim in the simulated PASS technique?",
        options: [
          "At the top of the smoke",
          "At the base of the fire",
          "At the exit",
        ],
        answer: 1,
      },
    ],
  },
  {
    id: "gas",
    title: "Gas Leak & Confined Space",
    minutes: 15,
    version: 1,
    description:
      "Practice hazard recognition, entry authorization, atmospheric checks and safe evacuation.",
    equipment:
      "Approved training area only. This app does not detect real gas or authorize entry.",
    steps: [
      {
        title: "Recognize the hazard",
        instruction:
          "Treat the marked zone as a simulated gas hazard. Tap the hazard marker without entering.",
        target: "hazard",
      },
      {
        title: "Raise the alarm",
        instruction:
          "Withdraw to safety and notify the control room. Tap the alarm marker.",
        target: "alarm",
      },
      {
        title: "Check the permit",
        instruction:
          "Entry requires an authorized permit and trained personnel. Tap the permit marker.",
        target: "permit",
      },
      {
        title: "Test the atmosphere",
        instruction:
          "A competent person must test the atmosphere with an approved instrument. Tap the gas detector.",
        target: "detector",
      },
      {
        title: "Check PPE",
        instruction:
          "Use PPE and respiratory protection specified by the permit and site risk assessment.",
        target: "ppe",
      },
      {
        title: "Confirm the attendant",
        instruction:
          "Confirm the standby attendant, communications and rescue plan. Never attempt unplanned rescue.",
        target: "buddy",
      },
      {
        title: "Evacuate",
        instruction:
          "On alarm, stop work and withdraw along the designated route. Tap the exit.",
        target: "exit",
      },
    ],
    questions: [
      {
        id: "g1",
        topic: "Atmosphere",
        text: "Can this phone tell you a confined space is free of gas?",
        options: [
          "Yes, using the camera",
          "No. Approved atmospheric testing is required",
          "Yes, if there is no smell",
        ],
        answer: 1,
      },
      {
        id: "g2",
        topic: "Permit",
        text: "What is required before confined-space entry?",
        options: [
          "Only a colleague’s permission",
          "A completed app lesson alone",
          "An authorized permit and all site entry controls",
        ],
        answer: 2,
      },
      {
        id: "g3",
        topic: "Rescue",
        text: "A worker collapses inside a confined space. What should you do?",
        options: [
          "Enter immediately alone",
          "Raise the alarm and activate the trained rescue response",
          "Ask an untrained worker to enter",
        ],
        answer: 1,
      },
      {
        id: "g4",
        topic: "Buddy system",
        text: "Who remains outside as part of the entry controls?",
        options: ["An appointed standby attendant", "Nobody", "A visitor"],
        answer: 0,
      },
      {
        id: "g5",
        topic: "Evacuation",
        text: "The gas alarm activates. What is the next action?",
        options: [
          "Finish the task",
          "Ignore it if you feel fine",
          "Stop work and evacuate under the site plan",
        ],
        answer: 2,
      },
    ],
  },
];
export const publicModules = () =>
  modules.map((m) => ({
    ...m,
    questions: m.questions.map(({ answer, ...q }) => q),
  }));
