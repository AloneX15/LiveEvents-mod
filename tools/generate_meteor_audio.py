"""Original procedural meteor audio. Requires numpy and soundfile; no third-party samples.
Run from any directory: python tools/generate_meteor_audio.py
"""
from pathlib import Path
import json
import numpy as np
import soundfile as sf

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'src/main/resources/assets/liveevents/sounds/meteor'
RATE = 44100

def noise(rng, n, low, high):
    spectrum = np.fft.rfft(rng.standard_normal(n))
    frequencies = np.fft.rfftfreq(n, 1/RATE)
    mask = (1-np.exp(-(frequencies/max(low,1))**4))*np.exp(-(frequencies/high)**4)
    result = np.fft.irfft(spectrum*mask, n)
    return result / max(np.std(result), 1e-9)

def render(kind, seconds, seed):
    rng = np.random.default_rng(seed)
    n = int(RATE*seconds)
    t = np.arange(n)/RATE
    if kind == 'whistle':
        progress = t/seconds
        phase = 2*np.pi*(210*t+100*t*t/seconds)
        audio = (noise(rng,n,90,2600)*0.25 + noise(rng,n,30,240)*0.4
                 + np.sin(phase)*0.12) * np.sin(np.pi*progress)**0.8 * (0.2+progress)
    elif kind == 'tail':
        audio = (noise(rng,n,24,180)*0.65 + noise(rng,n,120,1000)*0.12
                 + np.sin(2*np.pi*39*t)*0.12)*np.exp(-t/1.7)*(1-np.exp(-t/0.13))
    elif kind == 'debris':
        audio = noise(rng,n,240,4200)*0.05*np.exp(-t/0.9)
        for at in [0.03,0.13,0.27,0.46,0.72,1.05]:
            age=np.maximum(0,t-at)
            audio += noise(rng,n,380,6500)*0.18*np.exp(-age/0.045)*(t>=at)
    else:
        far = kind == 'impact_far'
        punch = np.sin(2*np.pi*(63*t+2.8*(1-np.exp(-t*19))))*np.exp(-t/0.30)
        body = noise(rng,n,24,240)*0.5*np.exp(-t/0.8)
        crack = noise(rng,n,300,1400 if far else 7500)*np.exp(-t/(0.18 if far else 0.055))
        audio = (punch*0.52+body+crack*(0.18 if far else 0.38))*(1-np.exp(-t/0.002))
    # Gentle saturation and headroom; short edge fades prevent discontinuities.
    audio = np.tanh(audio*1.15)
    audio *= np.minimum(1,t/0.006)*np.minimum(1,(seconds-t)/0.08)
    audio *= 0.79/max(np.max(np.abs(audio)),1e-9)
    OUT.mkdir(parents=True,exist_ok=True)
    sf.write(OUT/f'{kind}.ogg',audio,RATE,format='OGG',subtype='VORBIS')
    decoded,sr=sf.read(OUT/f'{kind}.ogg')
    assert sr==RATE and np.isfinite(decoded).all() and np.max(np.abs(decoded))<0.98
    print(f'{kind}: {seconds}s, peak={np.max(np.abs(decoded)):.3f}')

if __name__ == '__main__':
    for i,(kind,seconds) in enumerate([('whistle',2.6),('impact_near',2.8),('impact_far',3.8),('debris',2.0),('tail',6.0)]):
        render(kind,seconds,2107+i)
    path=ROOT/'src/main/resources/assets/liveevents/sounds.json'
    data=json.loads(path.read_text(encoding='utf-8'))
    for kind in ['whistle','impact_near','impact_far','debris','tail']:
        data[f'meteor.{kind}']={'subtitle':f'subtitles.liveevents.meteor.{kind}',
                              'sounds':[{'name':f'liveevents:meteor/{kind}'}]}
    path.write_text(json.dumps(data,indent=2)+'\n',encoding='utf-8')
