"""
Cheat Sheet Generator for Hayat AI
Generates print-ready PDF emergency protocol sheets in Arabic and English
for distribution in crisis zones where phones may not be available.
"""

import os
from pathlib import Path
from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.units import inch, cm
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle, PageBreak
from reportlab.lib.enums import TA_CENTER, TA_LEFT, TA_RIGHT
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont

# Medical protocols data
MEDICAL_PROTOCOLS = [
    {
        "title_en": "Severe Bleeding Control",
        "title_ar": "التحكم في النزيف الحاد",
        "steps_en": [
            "Apply direct pressure to the wound with a clean cloth",
            "If bleeding continues, apply a tourniquet 2-3 inches above wound",
            "Note the time of tourniquet application",
            "Do not remove the tourniquet once applied",
            "Seek medical help immediately"
        ],
        "steps_ar": [
            "اضغط مباشرة على الجرح بقطعة قماش نظيفة",
            "إذا استمر النزيف، ضع عاصبة على بعد 2-3 بوصات فوق الجرح",
            "دوّن وقت وضع العاصبة",
            "لا تزل العاصبة بمجرد وضعها",
            "اطلب المساعدة الطبية فوراً"
        ],
        "warning_en": "⚠️ Tourniquets only for life-threatening limb bleeding",
        "warning_ar": "⚠️ العاصبة فقط للنزيف الذي يهدد الحياة في الأطراف"
    },
    {
        "title_en": "Burn Treatment",
        "title_ar": "علاج الحروق",
        "steps_en": [
            "Remove person from source of burn",
            "Cool burn with running water for 10-20 minutes",
            "Remove jewelry or tight clothing near the burn",
            "Cover with sterile, non-stick bandage",
            "Do not pop blisters or apply ice directly"
        ],
        "steps_ar": [
            "أبعِد الشخص عن مصدر الحرق",
            "برّد الحرق بماء جارٍ لمدة 10-20 دقيقة",
            "أزِل أي مجوهرات أو ملابس ضيقة بالقرب من الحرق",
            "غطِّ بضمادة معقمة غير لاصقة",
            "لا تُفرّقِع البثور ولا تضع ثلجًا مباشرة"
        ],
        "warning_en": "⚠️ Never apply butter, oil, or ointments to fresh burns",
        "warning_ar": "⚠️ لا تضع زبدة أو زيت أو مراهم على الحروق الطازجة"
    },
    {
        "title_en": "Fracture Stabilization",
        "title_ar": "تثبيت الكسور",
        "steps_en": [
            "Check for breathing and consciousness",
            "Stop any bleeding with direct pressure",
            "Immobilize injured area with a splint",
            "Apply ice packs wrapped in cloth to reduce swelling",
            "Keep person still and comfortable"
        ],
        "steps_ar": [
            "تحقّق من التنفس والوعي",
            "أوقِف أي نزيف بالضغط المباشر",
            "ثبّت المنطقة المصابة باستخدام جبيرة",
            "ضع كمّادات ثلج ملفوفة بقطعة قماش",
            "أبقِ الشخص ساكنًا ومريحًا"
        ],
        "warning_en": "⚠️ Do not try to realign broken bones",
        "warning_ar": "⚠️ لا تحاول إعادة ضبط العظام المكسورة"
    },
    {
        "title_en": "CPR Basics",
        "title_ar": "أساسيات الإنعاش القلبي الرئوي",
        "steps_en": [
            "Check responsiveness and breathing",
            "Call for emergency help if available",
            "Begin chest compressions: push hard and fast (100-120/min)",
            "Push at least 2 inches deep in center of chest",
            "Allow full chest recoil between compressions",
            "Continue until help arrives or person shows signs of life"
        ],
        "steps_ar": [
            "تحقّق من الاستجابة والتنفس",
            "اتصل طلب المساعدة الطارئة إذا كان متاحًا",
            "ابدأ ضغطات الصدر: ادفع بقوة وبسرعة (100-120/دقيقة)",
            "ادفع على الأقل 2 بوصات عميقًا في منتصف الصدر",
            "اسمح بارتداد كامل الصدر بين الضغطات",
            "استمر حتى تصل المساعدة أو تظهر علامات الحياة"
        ],
        "warning_en": "⚠️ Ratio: 30 compressions to 2 rescue breaths if trained",
        "warning_ar": "⚠️ النسبة: 30 ضغطة إلى 2 نفس إنقاذ إذا كنت مدربًا"
    }
]


def create_cheat_sheet(output_path: str, language: str = "en"):
    """Create a single-page cheat sheet for emergency protocols."""
    
    doc = SimpleDocTemplate(
        output_path,
        pagesize=A4,
        rightMargin=1*cm,
        leftMargin=1*cm,
        topMargin=1*cm,
        bottomMargin=1*cm
    )
    
    styles = getSampleStyleSheet()
    
    # Custom styles
    title_style = ParagraphStyle(
        'CustomTitle',
        parent=styles['Heading1'],
        fontSize=18,
        textColor=colors.darkred,
        spaceAfter=12,
        alignment=TA_CENTER
    )
    
    step_style = ParagraphStyle(
        'StepStyle',
        parent=styles['Normal'],
        fontSize=10,
        spaceAfter=6,
        leading=14
    )
    
    warning_style = ParagraphStyle(
        'WarningStyle',
        parent=styles['Normal'],
        fontSize=9,
        textColor=colors.red,
        spaceBefore=6,
        alignment=TA_CENTER
    )
    
    header_style = ParagraphStyle(
        'HeaderStyle',
        parent=styles['Heading2'],
        fontSize=12,
        textColor=colors.darkblue,
        spaceBefore=12,
        spaceAfter=6
    )
    
    story = []
    
    # Header
    if language == "ar":
        story.append(Paragraph("Hayat AI - Emergency Protocols | بروتوكولات الطوارئ", title_style))
        story.append(Spacer(1, 0.2*inch))
        story.append(Paragraph("Quick Reference Guide for Crisis Situations", styles['Normal']))
    else:
        story.append(Paragraph("Hayat AI - Emergency Protocols", title_style))
        story.append(Spacer(1, 0.2*inch))
        story.append(Paragraph("Quick Reference Guide for Crisis Situations", styles['Normal']))
    
    story.append(Spacer(1, 0.3*inch))
    
    # Add each protocol
    for i, protocol in enumerate(MEDICAL_PROTOCOLS[:4]):  # Limit to 4 for one page
        if language == "ar":
            story.append(Paragraph(f"{i+1}. {protocol['title_ar']}", header_style))
            for step in protocol['steps_ar']:
                story.append(Paragraph(f"• {step}", step_style))
            story.append(Paragraph(protocol['warning_ar'], warning_style))
        else:
            story.append(Paragraph(f"{i+1}. {protocol['title_en']}", header_style))
            for step in protocol['steps_en']:
                story.append(Paragraph(f"• {step}", step_style))
            story.append(Paragraph(protocol['warning_en'], warning_style))
        
        if i < 3:
            story.append(Spacer(1, 0.15*inch))
    
    # Footer
    story.append(PageBreak())
    footer_style = ParagraphStyle(
        'FooterStyle',
        parent=styles['Normal'],
        fontSize=8,
        textColor=colors.gray,
        alignment=TA_CENTER
    )
    
    if language == "ar":
        story.append(Paragraph("Generated by Hayat AI - Offline Medical Assistant", footer_style))
        story.append(Paragraph("This is not a substitute for professional medical advice", footer_style))
    else:
        story.append(Paragraph("Generated by Hayat AI - Offline Medical Assistant", footer_style))
        story.append(Paragraph("This is not a substitute for professional medical advice", footer_style))
    
    doc.build(story)
    return True


def create_bilingual_cheat_sheet(output_path: str):
    """Create a bilingual (English-Arabic) cheat sheet with side-by-side comparison."""
    
    doc = SimpleDocTemplate(
        output_path,
        pagesize=A4,
        rightMargin=0.5*cm,
        leftMargin=0.5*cm,
        topMargin=1*cm,
        bottomMargin=1*cm
    )
    
    styles = getSampleStyleSheet()
    
    title_style = ParagraphStyle(
        'CustomTitle',
        parent=styles['Heading1'],
        fontSize=16,
        textColor=colors.darkred,
        spaceAfter=12,
        alignment=TA_CENTER
    )
    
    story = []
    
    # Header
    story.append(Paragraph("Hayat AI - Bilingual Emergency Protocols", title_style))
    story.append(Paragraph("بروتوكولات الطوارئ ثنائية اللغة", title_style))
    story.append(Spacer(1, 0.3*inch))
    
    # Create table for bilingual content
    for protocol in MEDICAL_PROTOCOLS[:3]:  # Limit to 3 for readability
        data = [
            [f"<b>{protocol['title_en']}</b>", f"<b>{protocol['title_ar']}</b>"]
        ]
        
        # Add steps side by side
        max_steps = max(len(protocol['steps_en']), len(protocol['steps_ar']))
        for j in range(max_steps):
            en_step = protocol['steps_en'][j] if j < len(protocol['steps_en']) else ""
            ar_step = protocol['steps_ar'][j] if j < len(protocol['steps_ar']) else ""
            data.append([f"• {en_step}", f"• {ar_step}"])
        
        # Add warnings
        data.append([
            f"<i>{protocol['warning_en']}</i>",
            f"<i>{protocol['warning_ar']}</i>"
        ])
        
        table = Table(data, colWidths=[3.5*inch, 3.5*inch])
        table.setStyle(TableStyle([
            ('BACKGROUND', (0, 0), (-1, 0), colors.lightgrey),
            ('TEXTCOLOR', (0, 0), (-1, 0), colors.black),
            ('ALIGN', (0, 0), (-1, -1), 'LEFT'),
            ('VALIGN', (0, 0), (-1, -1), 'TOP'),
            ('FONTNAME', (0, 0), (-1, 0), 'Helvetica-Bold'),
            ('FONTSIZE', (0, 0), (-1, -1), 9),
            ('BOTTOMPADDING', (0, 0), (-1, 0), 8),
            ('TOPPADDING', (0, 0), (-1, 0), 8),
            ('GRID', (0, 0), (-1, -1), 0.5, colors.grey),
            ('ROWBACKGROUNDS', (0, 1), (-1, -2), [colors.white, colors.HexColor('#f0f0f0')]),
        ]))
        
        story.append(table)
        story.append(Spacer(1, 0.2*inch))
    
    # Footer
    footer_style = ParagraphStyle(
        'FooterStyle',
        parent=styles['Normal'],
        fontSize=8,
        textColor=colors.gray,
        alignment=TA_CENTER
    )
    story.append(Paragraph("Generated by Hayat AI - Offline Medical Assistant", footer_style))
    story.append(Paragraph("This is not a substitute for professional medical advice", footer_style))
    
    doc.build(story)
    return True


if __name__ == "__main__":
    output_dir = Path("/workspace/output/cheat_sheets")
    output_dir.mkdir(parents=True, exist_ok=True)
    
    print("📄 Generating emergency protocol cheat sheets...\n")
    
    # Generate English version
    en_path = output_dir / "emergency_protocols_en.pdf"
    create_cheat_sheet(str(en_path), language="en")
    print(f"✓ English cheat sheet: {en_path}")
    
    # Generate Arabic version
    ar_path = output_dir / "emergency_protocols_ar.pdf"
    create_cheat_sheet(str(ar_path), language="ar")
    print(f"✓ Arabic cheat sheet: {ar_path}")
    
    # Generate bilingual version
    bilingual_path = output_dir / "emergency_protocols_bilingual.pdf"
    create_bilingual_cheat_sheet(str(bilingual_path))
    print(f"✓ Bilingual cheat sheet: {bilingual_path}")
    
    print(f"\n📊 All cheat sheets saved to: {output_dir}")
    print("\n💡 These PDFs can be printed and distributed in areas without phone access.")
