package io.github.mike10004.vhs.testsupport;

import static java.nio.file.FileVisitResult.CONTINUE;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

public class TemporaryFolder {
    private File rootFolder;

    public File newFile( String name ) throws IOException {
        File result = new File( rootFolder, name );
        result.createNewFile();
        return result;
    }

    void prepare() {
        try {
            rootFolder = File.createTempFile( "junit5-", ".tmp" );
        } catch( IOException ioe ) {
            throw new RuntimeException( ioe );
        }
        rootFolder.delete();
        rootFolder.mkdir();
    }

    void cleanUp() {
        try {
            deleteDirectory(rootFolder.toPath());
        } catch( IOException ioe ) {
            throw new RuntimeException( ioe );
        }
    }

    public static void deleteDirectory(Path directory) throws IOException {
        Files.walkFileTree(directory, new DeleteAllVisitor());
    }

    private static class DeleteAllVisitor extends SimpleFileVisitor<Path> {
        @Override
        public FileVisitResult visitFile( Path file, BasicFileAttributes attributes ) throws IOException {
            Files.delete( file );
            return CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory( Path directory, IOException exception ) throws IOException {
            Files.delete( directory );
            return CONTINUE;
        }
    }

}