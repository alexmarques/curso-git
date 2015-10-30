package br.com.itau.tj;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

import br.com.itau.tj.utils.FileUtils;

public class DefaultFileVisitor implements FileVisitor<Path> {
	
	private Path currentFile;
	
	public DefaultFileVisitor(Path currentFile) {
		this.currentFile = currentFile;
	}

	@Override
	public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
		return FileVisitResult.CONTINUE;
	}

	@Override
	public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
		try {
			FileTypeProvider provider = FileTypeFactory.getInstance().getFileTypeProvider(file);
			if(provider != null && provider.hasReferenceTo(currentFile)) {
				FileUtils.fileFounded = true;
				return FileVisitResult.TERMINATE;
			} 
		} catch (RuntimeException e) {
		}
		return FileVisitResult.CONTINUE;
	}

	@Override
	public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
		return FileVisitResult.CONTINUE;
	}

	@Override
	public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
		if(!FileUtils.fileFounded) {
			FileUtils.addUnusedFile(currentFile);
			FileUtils.fileFounded = false;
		}
		return FileVisitResult.CONTINUE;
	}

}


package br.com.itau.tj;

import java.nio.file.Path;
import java.nio.file.Paths;

public class FileTypeFactory {
	
	private static FileTypeFactory INSTANCE;
	
	private FileTypeFactory(){}
	
    public static FileTypeFactory getInstance() {
    	if(INSTANCE == null) {
    		INSTANCE = new FileTypeFactory();
    	}
    	return INSTANCE;
    }
    
    public FileTypeProvider getFileTypeProvider(String file) {
    	return getFileTypeProvider(Paths.get(file));
    }
    
    public FileTypeProvider getFileTypeProvider(Path file) {
    	String name = file.toString();
    	if(name.endsWith(".xsl") || name.endsWith(".xslt")) {
    		return new XSLFileTypeProvider(file);
    	}
    	return null;
    }
}

package br.com.itau.tj;

import java.nio.file.Path;

public abstract class FileTypeProvider implements Iterable<Path> {
	
	abstract void parseFile();

	abstract boolean hasReferenceTo(Path currentFile);

}


package br.com.itau.tj;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import br.com.itau.tj.utils.FileUtils;

public class LocalizarArquivosNaoUtilizados {

	private static Path relativePath = Paths.get("C:/ClearCase_Storage/vob/dancarv_TJ_jw_20_int/TJ_vob_jw/tjWeb/WebContent");
	
	public static void main(String [] args) throws Exception {
		FileUtils.setRelativePath(relativePath);
		varrerArquivos(Files.newDirectoryStream(relativePath));
		printUnusedFile();
	}
	
	private static void printUnusedFile() {
		for(Path path : FileUtils.getUnusedFiles()) {
			System.out.println(path.toAbsolutePath().toString());
		}
	}

	private static void varrerArquivos(DirectoryStream<Path> directoryStream) throws IOException {
		for(Path directory : directoryStream) {
			if(Files.isDirectory(directory)) {
				varrerArquivos(Files.newDirectoryStream(directory));
			} else {
				Files.walkFileTree(relativePath, new DefaultFileVisitor(directory));
			}
		}
	}
}

package br.com.itau.tj;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import br.com.itau.tj.utils.FileUtils;
import br.com.itau.tj.utils.StringUtils;

public class XSLFileTypeProvider extends FileTypeProvider implements Iterable<Path> {
	
	private Path path = null;
	private Set<Path> fileEntries = new HashSet<Path>();
	
	public XSLFileTypeProvider(Path path) {
		this.path = path;
		parseFile();
	}
	
	public XSLFileTypeProvider(String path) {
		this(Paths.get(path));
	}
	
	public void parseFile() {
		try {
			DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder;
			builder = builderFactory.newDocumentBuilder();
			Document document = builder.parse(path.toFile());
			document.getDocumentElement().normalize();
			lookForFileEntries(document.getChildNodes());
		} catch (ParserConfigurationException | SAXException | IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	private void lookForFileEntries(NodeList nodeList) {
		for(int count = 0; count < nodeList.getLength(); count++) {
			Node item = nodeList.item(count);
			String nodeValue = StringUtils.trim(item.getNodeValue());
			if(isSupportedFile(nodeValue)) {
				this.fileEntries.add(FileUtils.resolve(this.path, nodeValue));
			} else {
				if(item.hasAttributes()) {
					NamedNodeMap attributes = item.getAttributes(); 
					for(int countAttrs = 0; countAttrs < attributes.getLength(); countAttrs++) {
						Node attr = attributes.item(countAttrs);
						String attrNodeValue = StringUtils.trim(attr.getNodeValue());
						if(isSupportedFile(attr.getNodeValue())) {
							this.fileEntries.add(FileUtils.resolve(path, attrNodeValue));
						}
					}
				}
			}
			if(item.hasChildNodes()) {
				lookForFileEntries(item.getChildNodes());
			}
		}
	}
	
	private boolean isSupportedFile(String fileName) {
		return fileName != null && (fileName.endsWith(".xsl") || fileName.endsWith(".js") || fileName.endsWith(".css"));
	}

	@Override
	public Iterator<Path> iterator() {
		return this.fileEntries.iterator();
	}

	@Override
	boolean hasReferenceTo(Path currentFile) {
		Iterator<Path> iterator = this.iterator();
		while(iterator.hasNext()) {
			Path next = iterator.next();
			try {
				if(Files.exists(next)) {
					return Files.isSameFile(next, currentFile);
				} else {
					FileUtils.addInexistedFile(null, next);
					return false;
				}
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		return false;
	}
}


package br.com.itau.tj.utils;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

public class FileUtils {
	
	private static Set<Path> unusedFiles = new HashSet<>();
	private static Set<Path> inexistedFiles = new HashSet<Path>();
	private static Path relativePath;
	public static boolean fileFounded = false;
	
	public static void setRelativePath(Path relativePath) {
		FileUtils.relativePath = relativePath;
	}
	
	public static void addUnusedFile(Path path) {
		unusedFiles.add(path);
	}
	
	public static Set<Path> getUnusedFiles() {
		return unusedFiles;
	}
	
	public static void addInexistedFile(Path currentFile, Path fileDeclaredInsideCurrentFile) {
		inexistedFiles.add(fileDeclaredInsideCurrentFile);
	}
	
	public static Path resolve(Path path, String fileName) {
		if(path.toString().indexOf("Modal.js") != -1 || fileName.indexOf("Modal.js") != -1) {
			//System.out.println(fileName);
		}
		if(!fileName.startsWith("\\") && !fileName.startsWith("/")) {
			return path.resolveSibling(fileName).normalize();
		} else {
			return Paths.get(relativePath.toString(), fileName);
		}
	}
}


package br.com.itau.tj.utils;

public class StringUtils {
	
	public static String trim(String s) {
		return s != null ? s.trim() : "";
	}
}
