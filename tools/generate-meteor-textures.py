"""Original procedural meteor textures. Creado por TakumiStudios. Standard library only."""
from pathlib import Path
import math, random, struct, zlib
OUT=Path(__file__).resolve().parents[1]/'src/main/resources/assets/liveevents/textures/effect'
def save(name,w,pixels):
    def chunk(t,b):return struct.pack('>I',len(b))+t+b+struct.pack('>I',zlib.crc32(t+b)&0xffffffff)
    data=b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',w,w,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(bytes(pixels)))+chunk(b'IEND',b'')
    (OUT/name).write_bytes(data)
r=random.Random(2107);pixels=bytearray();w=64
for y in range(w):
    pixels.append(0)
    for x in range(w):
        n=r.randrange(24,65);crack=abs(math.sin(x*.29+y*.21+math.sin(y*.4)))<.055
        pixels.extend((150,46,12,255) if crack else (n,int(n*.8),int(n*.65),255))
save('meteor_rock.png',w,pixels)
pixels=bytearray();w=128
for y in range(w):
    pixels.append(0)
    for x in range(w):
        xx=(x-w/2)/(w/2);yy=(y-w/2)/(w/2)
        edge=max(0,1-xx*xx-yy*yy)
        noise=.5+.18*math.sin(x*.19+math.sin(y*.1)*3)+.15*math.sin(y*.24+math.sin(x*.13)*2)+.08*math.sin(x*.7+y*.5)
        alpha=int(255*edge**1.4*max(0,min(1,noise)))
        pixels.extend((230,230,230,alpha))
save('meteor_smoke.png',w,pixels)
