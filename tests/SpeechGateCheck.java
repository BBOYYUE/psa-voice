package com.genee.psavoice;
public final class SpeechGateCheck {
 public static void main(String[] args) {
  SpeechGate quiet = new SpeechGate();
  for(int ms=20;ms<5000;ms+=20) if(quiet.accept(10,ms)) throw new AssertionError("Silence stopped early");
  if(!quiet.accept(10,5000)||quiet.heardSpeech()) throw new AssertionError("Silence gate failed");
  SpeechGate speech = new SpeechGate();
  for(int ms=20;ms<=800;ms+=20) if(speech.accept(250,ms)) throw new AssertionError("Speech stopped early");
  for(int ms=820;ms<1600;ms+=20) if(speech.accept(10,ms)) throw new AssertionError("Pause stopped early");
  if(!speech.accept(10,1600)||!speech.heardSpeech()) throw new AssertionError("End of speech failed");
  SpeechGate click = new SpeechGate();
  click.accept(5000,20);
  for(int ms=40;ms<=5000;ms+=20) click.accept(10,ms);
  if(click.heardSpeech()) throw new AssertionError("Click mistaken for speech");
  SpeechGate continuous = new SpeechGate();
  for(int ms=20;ms<10000;ms+=20) if(continuous.accept(250,ms)) throw new AssertionError("Continuous speech stopped early");
  if(!continuous.accept(250,10000)) throw new AssertionError("Maximum duration failed");
  System.out.println("Speech gate checks passed: silence, speech ending, click rejection, maximum duration.");
 }
}
