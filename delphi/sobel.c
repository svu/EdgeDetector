/*
            This is an exact VC2005 replica of Delphi implementation
            for Sobel operator
                  Author: Mishka Na Servere, March 2010
*/
#include "stdafx.h"
#include "EdgeTest.h"
#include <stdlib.h>
#ifdef _MANAGED
#pragma managed(push, off)
#endif
BOOL APIENTRY DllMain( HMODULE hModule,
                         DWORD ul_reason_for_call,
                         LPVOID lpReserved
                                           )
{
          switch (ul_reason_for_call)
          {
          case DLL_PROCESS_ATTACH:
          case DLL_THREAD_ATTACH:
          case DLL_THREAD_DETACH:
          case DLL_PROCESS_DETACH:
                  break;
          }
     return TRUE;
}
#ifdef _MANAGED
#pragma managed(pop)
#endif
EDGETEST_API void __stdcall edge_test(unsigned char *Dst, unsigned char *Src,
                                        int AWidth, int AHeight, int ImgInc)
{
   unsigned char *L1, *L2, *L3, *L4;
   int y, x;
   int p1,p2,p3,p4,p6,p7,p5,p8;
   int h;
   int v;
   // set up pointers to 3 source lines (L1..L3) and destination line (L4)
   L1 = Src - ImgInc;
   L2 = Src;
   L3 = Src + ImgInc;
   L4 = Dst;
   for (y = 0; y < AHeight; y++)
   {
     for (x = 1; x < AWidth - 1; x++)
     {
       p1 = L1[x-1];
       p3 = L1[x+1];
       p4 = 2 * L2[x+1];
       p5 = L3[x+1] - p1;
       p6 = 2 * L3[x];
       p7 = L3[x-1] - p3;
       p8 = 2 * L2[x-1];
       p2 = 2 * L1[x];
       h = p4 + p5 - p8 - p7;
       v = p7 + p6 + p5 - p2;
       v = v < 0 ? -v : v;
       h = h < 0 ? -h : h;
       L4[x] = __min(255, __max(h,v));
     }
     L1 = L2;
     L2 = L3;
     L3 += ImgInc;
     L4 += ImgInc;
   }
}

