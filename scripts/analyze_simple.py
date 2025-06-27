#!/usr/bin/env python3
"""
Script simplificado y robusto para analizar resultados
"""

import csv
from pathlib import Path

def load_results():
    results_file = "results/detailed_results.csv"
    try:
        with open(results_file, 'r') as f:
            reader = csv.DictReader(f)
            data = list(reader)
        print(f"✓ Cargados {len(data)} resultados desde {results_file}")
        return data
    except FileNotFoundError:
        print(f"✗ No se encontró el archivo {results_file}")
        print("Ejecuta primero: ./test_uploads.sh")
        return None

def safe_float(value, default=0.0):
    """Conversión segura a float"""
    if not value or value == 'ERROR' or not value.strip():
        return default
    try:
        return float(value)
    except (ValueError, TypeError):
        return default

def analyze_data(data):
    print("\n=== ANÁLISIS DE RENDIMIENTO ===")
    print("=" * 50)
    
    # Agrupar por método
    methods = {}
    for row in data:
        method = row.get('Test', 'Unknown')
        if method not in methods:
            methods[method] = []
        
        throughput = safe_float(row.get('Throughput_MBps'))
        size_mb = safe_float(row.get('Size_MB'))
        
        if throughput > 0 and size_mb > 0:
            methods[method].append({
                'throughput': throughput,
                'size_mb': size_mb,
                'time': safe_float(row.get('Time_Bash'))
            })
    
    # Estadísticas por método
    print("\n📊 ESTADÍSTICAS POR MÉTODO:")
    print(f"{'Método':<25} {'Promedio':<12} {'Máximo':<12} {'Tests':<8}")
    print("-" * 60)
    
    method_stats = {}
    for method, results in methods.items():
        if results:
            throughputs = [r['throughput'] for r in results]
            avg = sum(throughputs) / len(throughputs)
            max_val = max(throughputs)
            method_stats[method] = {'avg': avg, 'max': max_val, 'count': len(results)}
            print(f"{method:<25} {avg:<12.1f} {max_val:<12.1f} {len(results):<8}")
    
    # Mejor método por tamaño
    print("\n🚀 MEJOR MÉTODO POR TAMAÑO:")
    sizes = set()
    for row in data:
        size = safe_float(row.get('Size_MB'))
        if size > 0:
            sizes.add(size)
    
    for size in sorted(sizes):
        best_throughput = 0
        best_method = ""
        
        for method, results in methods.items():
            for result in results:
                if result['size_mb'] == size and result['throughput'] > best_throughput:
                    best_throughput = result['throughput']
                    best_method = method
        
        if best_method:
            print(f"  {size}MB: {best_method} ({best_throughput:.1f} MB/s)")
    
    # Análisis HTTP/2
    http2_data = [row for row in data if 'HTTP/2' in row.get('Test', '')]
    if http2_data:
        print("\n🌐 ANÁLISIS HTTP/2:")
        
        frames_list = []
        frame_sizes = []
        
        for row in http2_data:
            frames = safe_float(row.get('Frames_Processed'))
            frame_size = safe_float(row.get('Avg_Frame_Size'))
            
            if frames > 0:
                frames_list.append(frames)
            if frame_size > 0:
                frame_sizes.append(frame_size)
        
        if frames_list:
            avg_frames = sum(frames_list) / len(frames_list)
            print(f"  Promedio frames procesados: {avg_frames:.0f}")
        
        if frame_sizes:
            avg_frame_size = sum(frame_sizes) / len(frame_sizes)
            print(f"  Tamaño promedio de frame: {avg_frame_size:.0f} bytes")
    
    # Crear tabla resumen
    print("\n📋 TABLA RESUMEN:")
    print("=" * 80)
    print(f"{'Método':<20} {'1MB':<8} {'10MB':<8} {'100MB':<8} {'Promedio':<10}")
    print("-" * 80)
    
    for method, results in methods.items():
        if not results:
            continue
            
        row_data = [method[:19]]
        throughputs = []
        
        # Buscar throughput para cada tamaño
        for target_size in [1.0, 10.0, 100.0]:
            found = False
            for result in results:
                if result['size_mb'] == target_size:
                    row_data.append(f"{result['throughput']:.1f}")
                    throughputs.append(result['throughput'])
                    found = True
                    break
            if not found:
                row_data.append("-")
        
        # Calcular promedio
        if throughputs:
            avg = sum(throughputs) / len(throughputs)
            row_data.append(f"{avg:.1f}")
        else:
            row_data.append("-")
        
        # Imprimir fila
        print(f"{row_data[0]:<20} {row_data[1]:<8} {row_data[2]:<8} {row_data[3]:<8} {row_data[4]:<10}")
    
    return method_stats

def generate_report(data, method_stats):
    """Generar reporte simple"""
    report_path = Path("results") / 'performance_report.txt'
    
    with open(report_path, 'w') as f:
        f.write("FILE UPLOAD PERFORMANCE REPORT\n")
        f.write("=" * 40 + "\n\n")
        
        if method_stats:
            best_method = max(method_stats.items(), key=lambda x: x[1]['avg'])
            f.write(f"MEJOR MÉTODO: {best_method[0]} ({best_method[1]['avg']:.1f} MB/s)\n\n")
        
        f.write("ESTADÍSTICAS:\n")
        for method, stats in method_stats.items():
            f.write(f"- {method}: {stats['avg']:.1f} MB/s promedio\n")
        
        # Análisis HTTP/2
        http2_data = [row for row in data if 'HTTP/2' in row.get('Test', '')]
        if http2_data:
            f.write(f"\nHTTP/2 FRAMES:\n")
            frames_list = []
            for row in http2_data:
                frames = safe_float(row.get('Frames_Processed'))
                if frames > 0:
                    frames_list.append(frames)
            
            if frames_list:
                avg_frames = sum(frames_list) / len(frames_list)
                f.write(f"- Promedio frames: {avg_frames:.0f}\n")
        
        f.write(f"\nRECOMENDACIONES:\n")
        if any('HTTP/2' in row.get('Test', '') for row in data):
            f.write("- Para máximo rendimiento: HTTP/2 Streaming\n")
            f.write("- Para archivos grandes: Upload Chunked\n")
            f.write("- Evitar HTTP/2 upgrade para archivos >1MB\n")
        else:
            f.write("- Para archivos grandes: Upload Chunked\n")
            f.write("- Para alta concurrencia: Upload Reactivo\n")
    
    print(f"\n✓ Reporte guardado en: {report_path}")

def main():
    data = load_results()
    if not data:
        return
    
    method_stats = analyze_data(data)
    generate_report(data, method_stats)
    
    print("\n" + "="*50)
    print("📊 ANÁLISIS COMPLETADO")
    print("="*50)
    print("📁 Archivo de datos: results/detailed_results.csv")
    print("📄 Reporte: results/performance_report.txt")

if __name__ == "__main__":
    main()