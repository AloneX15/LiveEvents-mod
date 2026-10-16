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
def hashed(x,y,seed):
    n=(x*374761393+y*668265263+seed*69069)&0xffffffff
    n=((n^(n>>13))*1274126177)&0xffffffff
    return (n^(n>>16))/4294967295

def noise(x,y,seed):
    xi,yi=math.floor(x),math.floor(y);fx,fy=x-xi,y-yi
    fx=fx*fx*(3-2*fx);fy=fy*fy*(3-2*fy)
    a,b,c,d=hashed(xi,yi,seed),hashed(xi+1,yi,seed),hashed(xi,yi+1,seed),hashed(xi+1,yi+1,seed)
    return a+(b-a)*fx+(c-a)*fy+(a-b-c+d)*fx*fy

pixels=bytearray();w=128
for y in range(w):
    pixels.append(0)
    for x in range(w):
        xx=(x-w/2)/(w/2);yy=(y-w/2)/(w/2);edge=max(0,1-xx*xx-yy*yy)
        n=sum(noise(x/(32/(2**o)),y/(32/(2**o)),2107+o*31)*(.5**(o+1)) for o in range(5))/(1-.5**5)
        alpha=int(max(0,min(255,255*max(0,n*1.6-.45)**1.3*edge)))
        pixels.extend((255,255,255,alpha))
save('meteor_smoke.png',w,pixels)
