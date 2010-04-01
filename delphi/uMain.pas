{-----------------------------------------------------------------------
           Sample implementaion for modified Sobel operator
                ** Compilers becnhmarking purposes **
                Author: Mishka Na Servere, March 2010
------------------------------------------------------------------------}
unit uMain;
interface
uses
  Windows, Messages, SysUtils, Variants, Classes, Graphics, Controls, Forms,
  Dialogs, avPerfTmr, imsImage, ExtCtrls, imsDisplay, ComCtrls, ToolWin,
  StdCtrls, ExtDlgs, math, AppEvnts, Spin, SyncObjs;
type
  TProcParam = record
     Start : integer;
     Height : integer;
  end;
  TProcThread = class(TThread)
  private
     FIndex : integer;
  protected
     procedure Execute; override;
  public
     constructor Create(Ix:integer);
  end;
  TCompiler = (cmDelphi, cmVC, cmAsm);
  TSobelProc = procedure (Ix:integer);
  TfMain = class(TForm)
     PerfTmr: TavPerfTmr;
     Scrn: TimsDisplay;
     Status: TStatusBar;
     Panel1: TPanel;
     btOpen: TButton;
     btSave: TButton;
     btProcA: TButton;
     OpenDlg: TOpenPictureDialog;
     SaveDlg: TSavePictureDialog;
     btProcD: TButton;
     btReop: TButton;
     AppEvnt: TApplicationEvents;
     spThreads: TSpinEdit;
     Label1: TLabel;
     btProcVC: TButton;
     procedure AppEvntException(Sender: TObject; E: Exception);
     procedure btReopClick(Sender: TObject);
     procedure SaveDlgTypeChange(Sender: TObject);
     procedure btSaveClick(Sender: TObject);
     procedure btProcDClick(Sender: TObject);
     procedure FormDestroy(Sender: TObject);
     procedure FormCreate(Sender: TObject);
     procedure btOpenClick(Sender: TObject);
  private
     FThrdNum : integer;
     FName    : string;
     FImg     : TimsImgC3;
     FImgD    : TimsImgC3;
     FPrmArr : array[0..15] of TProcParam;
     FSct     :TCriticalSection;
     FSem     :TEvent;
     FCmp     : TCompiler;
     procedure SobelX(Dst,Src:PByteArray; AWidth,AHeight,ImgInc:integer); stdcall;
  public
     { Public declarations }
  end;
var
  fMain: TfMain;
implementation
{$R *.dfm}
procedure EdgeTestC(Dst,Src:PByteArray; AWidth,AHeight,ImgInc:integer); stdcall;
                                     external 'edgetest.dll' name '_edge_test@20';
// ------ Call to asm processor -------
procedure ProcAsm(Ix: integer);
var
  n : integer;
begin
  with fMain do
  for n := 0 to 2 do
     SobelX(PByteArray(FImgD.Pix.C3[n][FPrmArr[Ix].Start]),
             PByteArray(FImg.Pix.C3[n][FPrmArr[Ix].Start]),
             FImg.Width, FPrmArr[Ix].Height, FImg.Pitch);
end;
// ------ Native (Delphi) processor -------
procedure ProcNative(Ix : integer);
var
  n: Integer;
  L1: PByteArray;
  L2: PByteArray;
  L3: PByteArray;
  L4: PByteArray;
  y: Integer;
  x: Integer;
  p1,p2,p3,p4,p6,P7,P5,p8 : integer;
  h: integer;
  v: integer;
  DstY1, DstH : integer;
begin
  with fMain do
  begin
     DstY1 := FPrmArr[Ix].Start;
     DstH := FPrmArr[Ix].Height;
     for n := 0 to 2 do
     // for each colour plane
     begin
       // set up pointers to 3 source lines (L1..L3) and destination line (L4)
       L1 := PByteArray(FImg.Pix.C3[n][DstY1 - 1]);
       L2 := pointer(integer(L1) + FImg.Pitch);
       L3 := pointer(integer(L2) + FImg.Pitch);
       L4 := PByteArray(FImgD.Pix.C3[n][DstY1]);
       for y := DstY1 to DstY1 + DstH do
       // for each line
       begin
          for x := 1 to FImg.Width - 2 do
          // for each column
          begin
            // minimize number of memory readings with byte->integer conversion
            p1 := L1[x-1];
            p3 := L1[x+1];
            P4 := 2 * L2[x+1];
            P5 := L3[x+1] - p1;
            P6 := 2 * L3[x];
            P7 := L3[x-1] - p3;
            P8 := 2 * L2[x-1];
            p2 := 2 * L1[x];
            // compute modified Sobel
            h := p4 + p5 - p8 - p7;
            v := p7 + p6 + p5 - p2;
            // "manual" abs() coding: faster
            if v < 0 then
               v := -v;
            if h < 0 then
               h := -h;
            // result saving
            L4[x] := Min(255, Max(h,v));
          end;
          // update pointers
          L1 := L2;
          L2 := L3;
          L3 := pointer(integer(L3) + FImg.Pitch);
          L4 := pointer(integer(L4) + FImg.Pitch);
       end;
     end;
  end;
end;
// ------ Call to VC 2005 processor (supplied with DLL) -------
procedure ProcVC(Ix: integer);
var
  n : integer;
begin
  with fMain do
  for n := 0 to 2 do
     EdgeTestC(PByteArray(FImgD.Pix.C3[n][FPrmArr[Ix].Start]),
                 PByteArray(FImg.Pix.C3[n][FPrmArr[Ix].Start]),
                 FImg.Width, FPrmArr[Ix].Height, FImg.Pitch);
end;
// processing procedures
const
  SobelProcs : array[cmDelphi .. cmAsm] of TSobelProc =
  (
     ProcNative, ProcVC, ProcAsm
  );
procedure TfMain.AppEvntException(Sender: TObject; E: Exception);
begin
  MessageDlg('Unexpected error',mtError,[mbOK],0);
end;
procedure TfMain.btOpenClick(Sender: TObject);
begin
  if OpenDlg.Execute then
  begin
     Screen.Cursor := crHourGlass;
     try
       if not Assigned(FImg) then
          FImg := TimsImgC3.Create(1,1);
       try
          FImg.LoadFromFile(OpenDlg.FileName);
          if (FImg.Width < 8) or (FImg.Height < 8) then
          begin
            FreeAndNil(FImg);
            MessageDlg('Image dimensions must be greater than 8x8',mtError,[mbOK],0);
          end
          else
          begin
            FName := OpenDlg.FileName;
            FImg.Render(Scrn,[rfAutoRefresh]);
            Status.Panels[1].Text := '';
            Status.Panels[0].Text := Format('%d x %d',[FImg.Width,FImg.Height]);
          end;
       except
          MessageDlg(Format('Cannot open "%s"',[OpenDlg.FileName]),mtError,[mbOK],0);
       end;
     finally
       Screen.Cursor := crDefault;
     end;
  end;
end;
// Handler for "Assembler", "Delphi", and "VC 2005" buttons
procedure TfMain.btProcDClick(Sender: TObject);
var
  ys, s, i, b : integer;
  MThread : boolean;
begin
  if Assigned(FImg) then
  begin
     Screen.Cursor := crHourGlass;
     try
       try
          if Sender = btProcA then
            FCmp := cmAsm
          else
          if Sender = btProcD then
            FCmp := cmDelphi
          else
            FCmp := cmVC;
          // Asm will use multiple threads for large images only, as it is slow
          // to switch SSE context and performance suffers on small images
          MThread := (spThreads.Value > 1) and
              ((FCmp <> cmAsm) or (FCmp = cmAsm) and (FImg.ImgSize > 10000000));
          // Start measuring time with high-precision timer object (1 uS accuracy)
          PerfTmr.Active := true;
          // Create destination image, don't fill with 0s
          if not Assigned(FImgD) then
            FImgD := TimsImgC3.Create(FImg.Width,FImg.Height,false)
          else
            FImgD.Resize(FImg.Width,FImg.Height);
          try
            // ........ multiple threads .........
            if MThread then
            begin
               // prepare threads
               FThrdNum := spThreads.Value;
               s := (FImg.Height - 2) div FThrdNum;
               ys := 1;
               FSem.ResetEvent;
               b := spThreads.Value - 1;
               // fill parameters for each thread and start them all
               for i := 0 to b do
               begin
                 FPrmArr[i].Start := ys;
                 // last tile height can differ of others
                 if i = b then
                   FPrmArr[i].Height := FImg.Height - ys
                 else
                   FPrmArr[i].Height := s;
                 inc(ys,s);
                 TProcThread.Create(i);
               end;
               // wait for threads completion
               FSem.WaitFor(INFINITE);
            end
            // ......... single thread .........
            else
            begin
               FPrmArr[0].Height := FImg.Height - 2;
               FPrmArr[0].Start := 1;
               SobelProcs[FCmp](0);
            end;
            PerfTmr.Active := false; // all done in FImgD: stop measuring time
            FImgD.Render(Scrn,[rfAutoRefresh]);
          finally
            // Stop measuring time in case we got exception in this try-finally block
            // (Normally does nothing as timer should be already stopped above)
            PerfTmr.Active := false;
          end;
       except
          MessageDlg('There are problems processing image',mtError,[mbOK],0);
       end;
       Status.Panels[1].Text := Format('Done in %6.2f ms',[PerfTmr.Interval]);
     finally
       Screen.Cursor := crDefault;
     end;
  end
  else
     MessageDlg('No image to process',mtWarning,[mbOK],0);
end;
procedure TfMain.btReopClick(Sender: TObject);
begin
  if (FName = '') or not Assigned(FImg) then
  begin
     MessageDlg('Image was not opened yet',mtWarning,[mbOK],0);
     exit;
  end;
  Screen.Cursor := crHourGlass;
  try
     try
       FImg.LoadFromFile(FName);
       FImg.Render(Scrn,[rfAutoRefresh]);
       Status.Panels[1].Text := '';
       Status.Panels[0].Text := Format('%d x %d',[FImg.Width,FImg.Height]);
     except
       MessageDlg(Format('Cannot open "%s"',[FName]),mtError,[mbOK],0);
     end;
  finally
     Screen.Cursor := crDefault;
  end;
end;
procedure TfMain.btSaveClick(Sender: TObject);
begin
  if Assigned(FImgD) then
  begin
     if SaveDlg.Execute then
     try
       Screen.Cursor := crHourGlass;
       try
          FImgD.SaveToFile(SaveDlg.FileName);
          Status.Panels[1].Text := '';
       finally
          Screen.Cursor := crDefault;
       end;
     except
       MessageDlg(Format('Cannot save "%s"',[SaveDlg.FileName]),mtError,[mbOK],0);
     end;
  end
  else
     MessageDlg('No image to save',mtWarning,[mbOK],0);
end;
procedure TfMain.FormCreate(Sender: TObject);
begin
  FImg := nil;
  FCmp := cmDelphi;
  FImgD := nil;
  FSct := TCriticalSection.Create;
  FSem := TEvent.Create(nil,true,false,'');
  FName := '';
end;
procedure TfMain.FormDestroy(Sender: TObject);
begin
  FSem.Free;
  FSct.Free;
  FImgD.Free;
  FImg.Free;
end;
procedure TfMain.SaveDlgTypeChange(Sender: TObject);
begin
  if SaveDlg.FilterIndex = 0 then
     SaveDlg.DefaultExt := 'bmp'
  else
     SaveDlg.DefaultExt := 'jpg';
end;
{ --------------------------------------------
     Asm implementation is shown partially
       *** Author's proprietary code ***
 ---------------------------------------------}
procedure TfMain.SobelX(Dst, Src: PByteArray; AWidth, AHeight, ImgInc: integer);
var
 DstT,SrcT:integer;
begin
  asm
      PUSHAD
      MOV EAX,Dst
      MOV DstT,EAX
      MOV EAX,Src
      MOV SrcT,EAX
      MOV EDX,ImgInc
      MOV ECX,AWidth
      SUB ECX,3
      TEST ECX,15
      JZ @@SblX_1
      ADD ECX,16
 @@SblX_1:
      SHR ECX,4
      MOV EAX,AHeight
      SUB EAX,2
      PSUBUSB XMM5,XMM5
 @@SblX_Cy:
      MOV EBX,ECX
      MOV ESI,SrcT
      MOV EDI,DstT
 @@SblX_Cx:
      MOVDQA XMM1,[ESI]
      MOVDQA XMM3,XMM1
      MOVDQA XMM2,[ESI+EDX*2]
      PSUBUSB XMM1,XMM2
      PSUBUSB XMM2,XMM3
      { --------------------------------------------
                 40 lines removed here
            *** Author's proprietary code ***
       ---------------------------------------------}
      MOVDQU [EDI+EDX+1],XMM4
      ADD EDI,16
      ADD ESI,16
      SUB EBX,1
      JNZ @@SblX_Cx
      ADD SrcT,EDX
      ADD DstT,EDX
      SUB EAX,1
      JNZ @@SblX_Cy
      POPAD
    end;
end;
{ TProcThread }
constructor TProcThread.Create(Ix: integer);
begin
  inherited Create(true);
  FreeOnTerminate := true;
  FIndex := Ix;
  Resume;
end;
procedure TProcThread.Execute;
begin
  with fMain do
  begin
     SobelProcs[FCmp](FIndex);
     FSct.Acquire;
     try
       dec(FThrdNum);
       if FThrdNum = 0 then
          FSem.SetEvent;
     finally
       FSct.Release;
     end;
  end;
end;
end.

