<#
.SYNOPSIS
    Excelファイル(.xlsx/.xlsm/.xls)をPDFに変換する。AIにExcel設計書を読ませる前処理用。

.DESCRIPTION
    Microsoft Excel のCOM操作(ExportAsFixedFormat)でPDF化する。
    シートごとの印刷設定(印刷範囲・用紙・向き)がそのまま反映されるため、
    人が印刷したときと同じレイアウトでAIに渡せる。

    Excelがインストールされていないマシンでは LibreOffice でも代替可能:
      soffice --headless --convert-to pdf --outdir <出力先> <入力.xlsx>

.PARAMETER Path
    変換対象。ファイルまたはフォルダ(フォルダの場合は直下の .xlsx/.xlsm/.xls をすべて変換)。

.PARAMETER OutDir
    出力先フォルダ。省略時は入力ファイルと同じ場所。

.PARAMETER PerSheet
    指定すると「1シート=1PDF」で出力する(ファイル名: <ブック名>_<連番>_<シート名>.pdf)。
    省略時はブック全体を1つのPDFにする。
    大きいブックをAIに部分的に読ませたい場合はPerSheetが便利。

.EXAMPLE
    .\Convert-ExcelToPdf.ps1 -Path "C:\docs\設計書.xlsx"

.EXAMPLE
    .\Convert-ExcelToPdf.ps1 -Path "C:\docs" -OutDir "C:\docs\pdf" -PerSheet
#>
param(
    [Parameter(Mandatory = $true)]
    [string]$Path,

    [string]$OutDir,

    [switch]$PerSheet
)

$ErrorActionPreference = 'Stop'

# 入力の解決(ファイル or フォルダ)
$item = Get-Item -LiteralPath $Path
if ($item.PSIsContainer) {
    $targets = Get-ChildItem -LiteralPath $item.FullName -File |
        Where-Object { $_.Extension -in '.xlsx', '.xlsm', '.xls' -and $_.Name -notlike '~$*' }
    if (-not $targets) {
        Write-Error "変換対象のExcelファイルが見つかりません: $Path"
    }
} else {
    $targets = @($item)
}

if ($OutDir) {
    New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
    $OutDir = (Get-Item -LiteralPath $OutDir).FullName
}

# シート名をファイル名に使えるようサニタイズ
function Sanitize([string]$name) {
    return ($name -replace '[\\/:*?"<>|]', '_').Trim()
}

$xlTypePDF = 0
$xlSheetVisible = -1

$excel = $null
try {
    $excel = New-Object -ComObject Excel.Application
    $excel.Visible = $false
    $excel.DisplayAlerts = $false
    # 再計算や外部リンク更新のダイアログを抑止
    $excel.AskToUpdateLinks = $false

    foreach ($file in $targets) {
        $baseDir = if ($OutDir) { $OutDir } else { $file.DirectoryName }
        $baseName = [IO.Path]::GetFileNameWithoutExtension($file.Name)
        Write-Host "変換中: $($file.Name)"

        $wb = $excel.Workbooks.Open($file.FullName, 0, $true)  # UpdateLinks=0, ReadOnly=true
        try {
            if ($PerSheet) {
                $index = 0
                foreach ($ws in $wb.Worksheets) {
                    $index++
                    if ($ws.Visible -ne $xlSheetVisible) {
                        Write-Host ("  スキップ(非表示): {0}" -f $ws.Name)
                        continue
                    }
                    $outPath = Join-Path $baseDir ("{0}_{1:d2}_{2}.pdf" -f $baseName, $index, (Sanitize $ws.Name))
                    $ws.ExportAsFixedFormat($xlTypePDF, $outPath)
                    Write-Host "  -> $outPath"
                }
            } else {
                $outPath = Join-Path $baseDir ("{0}.pdf" -f $baseName)
                $wb.ExportAsFixedFormat($xlTypePDF, $outPath)
                Write-Host "  -> $outPath"
            }
        } finally {
            $wb.Close($false)
        }
    }
} finally {
    if ($excel) {
        $excel.Quit()
        [void][Runtime.InteropServices.Marshal]::ReleaseComObject($excel)
    }
}

Write-Host "完了"
