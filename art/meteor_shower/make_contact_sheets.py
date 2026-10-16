"""Create review sheets from rendered stages and views, using Blender's image API."""
from pathlib import Path
import bpy,json
root=Path(__file__).resolve().parent/'generated'/'v2'
for variant in ('earth','water','snow'):
    folder=root/variant
    for name,files,columns in [('stages',[folder/('stage_%03d.png'%f) for f in (24,36,55,76,100,140,205,240)],4),
                               ('views',[folder/('view_%02d.png'%i) for i in range(5)],3)]:
        width,height=480,270;rows=(len(files)+columns-1)//columns
        sheet=bpy.data.images.new(variant+' '+name,width=width*columns,height=height*rows,alpha=True)
        pixels=[0.0]*(width*columns*height*rows*4)
        for i,path in enumerate(files):
            tile=bpy.data.images.load(str(path),check_existing=False);source=list(tile.pixels)
            for row in range(height):
                dest=(((rows-1-i//columns)*height+row)*width*columns+i%columns*width)*4
                pixels[dest:dest+width*4]=source[row*width*4:(row+1)*width*4]
            bpy.data.images.remove(tile)
        sheet.pixels.foreach_set(pixels);sheet.filepath_raw=str(folder/(name+'.png'));sheet.file_format='PNG';sheet.save();bpy.data.images.remove(sheet)
